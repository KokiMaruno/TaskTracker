(ns tasktracker.app.spec.task-test
  "task spec のテスト"
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [tasktracker.app.spec.task :as task]
            [tasktracker.app.spec.core :as core]))

;; テスト用カスタムジェネレータ
(def test-generators
  {::core/gitlab-url 
   #(gen/fmap (fn [path] (str "https://gitlab.example.com/" path))
              (gen/string-alphanumeric))})

;; spec check のラッパー（カスタムジェネレータ付き）
(deftest spec-check-test
  (testing "全関数の spec check"
    (let [results (stest/check (stest/enumerate-namespace 'tasktracker.app.spec.task)
                               {:gen test-generators})]
      (doseq [result results]
        (is (-> result :clojure.spec.test.check/ret :result)
            (str "Function " (:sym result) " failed spec check"))))))

;; GitLab連携の特定のビジネスロジックテスト
(deftest gitlab-integration-test
  (testing "create-task-from-gitlab でのデータ変換"
    (let [gitlab-task {::task/gitlab-issue-id 123
                       ::task/gitlab-project-id 456
                       ::task/title "  Test Task  "
                       ::task/status :open
                       ::task/project-name "test-project"
                       ::task/labels ["  bug  " "feature" ""]}
          task-id 1
          result (task/create-task-from-gitlab gitlab-task task-id)]
      
      ;; タイトルの正規化
      (is (= "Test Task" (::task/title result)))
      
      ;; ラベルの正規化（空文字除去）
      (is (= ["bug" "feature"] (::task/labels result)))
      
      ;; 必須フィールドの設定
      (is (= task-id (::task/task-id result)))
      (is (true? (::task/is-synced result)))
      (is (some? (::task/created-at result)))
      (is (some? (::task/updated-at result)))))
  
  (testing "sync-task-from-gitlab での更新"
    (let [existing-task {::task/task-id 1
                         ::task/gitlab-issue-id 123
                         ::task/title "Old Title"
                         ::task/local-memo "My memo"
                         ::task/is-synced false
                         ::task/created-at (java.util.Date. 1000000000)
                         ::task/updated-at (java.util.Date. 1000000000)}
          gitlab-task {::task/gitlab-issue-id 123
                       ::task/title "  New Title  "
                       ::task/status :in-progress
                       ::task/labels ["updated"]}
          result (task/sync-task-from-gitlab existing-task gitlab-task)]
      
      ;; GitLabデータで更新される
      (is (= "New Title" (::task/title result)))
      (is (= :in-progress (::task/status result)))
      (is (= ["updated"] (::task/labels result)))
      
      ;; ローカルデータは保持される
      (is (= "My memo" (::task/local-memo result)))
      (is (= 1 (::task/task-id result)))
      
      ;; 同期状態が更新される
      (is (true? (::task/is-synced result)))
      (is (.after (::task/updated-at result) (::task/created-at existing-task))))))

;; フィルタリングのビジネスロジックテスト
(deftest filtering-test
  (testing "filter-tasks の複合条件"
    (let [tasks [{::task/task-id 1 ::task/status :open ::task/project-name "proj-a" ::task/labels ["bug"]}
                 {::task/task-id 2 ::task/status :closed ::task/project-name "proj-a" ::task/labels ["feature"]}
                 {::task/task-id 3 ::task/status :open ::task/project-name "proj-b" ::task/labels ["bug" "critical"]}]
          
          ;; ステータスフィルタ
          open-tasks (task/filter-tasks tasks {::task/status :open})
          
          ;; プロジェクト + ラベルフィルタ
          proj-a-bugs (task/filter-tasks tasks {::task/project-name "proj-a" ::task/labels ["bug"]})]
      
      (is (= 2 (count open-tasks)))
      (is (= #{1 3} (set (map ::task/task-id open-tasks))))
      
      (is (= 1 (count proj-a-bugs)))
      (is (= 1 (::task/task-id (first proj-a-bugs)))))))

;; 同期判定のビジネスロジックテスト  
(deftest sync-logic-test
  (testing "task-needs-sync? の判定ロジック"
    (let [now (java.util.Date.)
          old-time (java.util.Date. (- (.getTime now) 3600000)) ; 1時間前
          
          synced-recent {::task/is-synced true ::task/last-synced-at now}
          synced-old {::task/is-synced true ::task/last-synced-at old-time}  
          unsynced {::task/is-synced false}]
      
      ;; 最近同期済み → sync不要
      (is (false? (task/task-needs-sync? synced-recent 30)))
      
      ;; 古い同期 → sync必要
      (is (true? (task/task-needs-sync? synced-old 30)))
      
      ;; 未同期 → sync必要
      (is (true? (task/task-needs-sync? unsynced 30))))))
