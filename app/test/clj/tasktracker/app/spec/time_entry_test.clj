(ns tasktracker.app.spec.time-entry-test
  "time-entry spec のテスト"
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [tasktracker.app.spec.time-entry :as time-entry]
            [tasktracker.app.spec.core :as core]))

;; テスト用カスタムジェネレータ
(def test-generators
  {::core/gitlab-url 
   #(gen/fmap (fn [path] (str "https://gitlab.example.com/" path))
              (gen/string-alphanumeric))})

;; spec check のラッパー（カスタムジェネレータ付き）
(deftest spec-check-test
  (testing "全関数の spec check"
    (let [results (stest/check (stest/enumerate-namespace 'tasktracker.app.spec.time-entry)
                               {:gen test-generators})]
      (doseq [result results]
        (is (-> result :clojure.spec.test.check/ret :result)
            (str "Function " (:sym result) " failed spec check"))))))

;; 時間記録ライフサイクルのビジネスロジックテスト
(deftest time-entry-lifecycle-test
  (testing "時間記録の開始→停止のフロー"
    (let [start-request {::time-entry/gitlab-issue-id 123
                         ::time-entry/memo "作業開始"}
          entry-id 1
          
          ;; 開始
          running-entry (time-entry/start-time-entry start-request entry-id)]
      
      ;; 少し待つ（時間差を作るため）
      (Thread/sleep 10)
      
      (let [;; 停止
            completed-entry (time-entry/stop-time-entry running-entry "作業完了")]
        
        ;; 開始時の状態確認
        (is (= entry-id (::time-entry/entry-id running-entry)))
        (is (= 123 (::time-entry/gitlab-issue-id running-entry)))
        (is (= :running (::time-entry/status running-entry)))
        (is (= "作業開始" (::time-entry/memo running-entry)))
        (is (some? (::time-entry/start-time running-entry)))
        (is (nil? (::time-entry/end-time running-entry)))
        
        ;; 停止時の状態確認
        (is (= :completed (::time-entry/status completed-entry)))
        (is (= "作業完了" (::time-entry/memo completed-entry)))
        (is (some? (::time-entry/end-time completed-entry)))
        (is (some? (::time-entry/duration-minutes completed-entry)))
        (is (.before (::time-entry/start-time completed-entry) 
                     (::time-entry/end-time completed-entry))))))
  
  (testing "手動時間記録の更新"
    (let [original-entry {::time-entry/entry-id 1
                          ::time-entry/gitlab-issue-id 123
                          ::time-entry/start-time (java.util.Date. 1000000000)
                          ::time-entry/end-time (java.util.Date. 1000060000)
                          ::time-entry/duration-minutes 1
                          ::time-entry/status :completed
                          ::time-entry/memo "元のメモ"
                          ::time-entry/created-at (java.util.Date. 1000000000)
                          ::time-entry/updated-at (java.util.Date. 1000000000)}
          
          update-request {::time-entry/entry-id 1
                          ::time-entry/start-time (java.util.Date. 1000000000)
                          ::time-entry/end-time (java.util.Date. 1000180000)  ; 3分後に変更
                          ::time-entry/memo "  更新されたメモ  "}
          
          updated-entry (time-entry/update-time-entry original-entry update-request)]
      
      ;; 更新された内容
      (is (= (java.util.Date. 1000180000) (::time-entry/end-time updated-entry)))
      (is (= "更新されたメモ" (::time-entry/memo updated-entry)))
      (is (= 3 (::time-entry/duration-minutes updated-entry)))  ; 自動再計算
      
      ;; 変更されない内容
      (is (= 1 (::time-entry/entry-id updated-entry)))
      (is (= 123 (::time-entry/gitlab-issue-id updated-entry)))
      (is (= :completed (::time-entry/status updated-entry)))
      
      ;; updated-at が更新されている
      (is (.after (::time-entry/updated-at updated-entry) 
                  (::time-entry/created-at original-entry))))))

;; 時間計算のビジネスロジックテスト
(deftest time-calculation-test
  (testing "calculate-duration の正確性"
    (let [start (java.util.Date. 1000000000)
          end-1min (java.util.Date. 1000060000)   ; 1分後
          end-5min (java.util.Date. 1000300000)   ; 5分後
          end-1hour (java.util.Date. 1003600000)] ; 1時間後
      
      (is (= 1 (time-entry/calculate-duration start end-1min)))
      (is (= 5 (time-entry/calculate-duration start end-5min)))
      (is (= 60 (time-entry/calculate-duration start end-1hour)))))
  
  (testing "format-duration の表示形式"
    (is (= "30分" (time-entry/format-duration 30)))
    (is (= "1時間" (time-entry/format-duration 60)))
    (is (= "1時間30分" (time-entry/format-duration 90)))
    (is (= "2時間15分" (time-entry/format-duration 135)))))

;; 状態判定のビジネスロジックテスト
(deftest status-check-test
  (testing "running-entry? の判定"
    (let [running-entry {::time-entry/status :running}
          completed-entry {::time-entry/status :completed}]
      
      (is (true? (time-entry/running-entry? running-entry)))
      (is (false? (time-entry/running-entry? completed-entry)))))
  
  (testing "completed-entry? の判定"
    (let [incomplete-entry {::time-entry/status :completed
                            ::time-entry/end-time (java.util.Date.)
                            ;; duration-minutes がない
                            }
          complete-entry {::time-entry/status :completed
                          ::time-entry/end-time (java.util.Date.)
                          ::time-entry/duration-minutes 60}
          running-entry {::time-entry/status :running}]
      
      (is (false? (time-entry/completed-entry? incomplete-entry)))
      (is (true? (time-entry/completed-entry? complete-entry)))
      (is (false? (time-entry/completed-entry? running-entry))))))

;; フィルタリングのビジネスロジックテスト
(deftest filtering-test
  (testing "filter-time-entries の複合条件"
    (let [entries [{::time-entry/entry-id 1 
                    ::time-entry/gitlab-issue-id 123 
                    ::time-entry/status :running
                    ::time-entry/start-time (java.util.Date. 1000000000)}
                   {::time-entry/entry-id 2 
                    ::time-entry/gitlab-issue-id 456 
                    ::time-entry/status :completed
                    ::time-entry/start-time (java.util.Date. 1000060000)}
                   {::time-entry/entry-id 3 
                    ::time-entry/gitlab-issue-id 123 
                    ::time-entry/status :completed
                    ::time-entry/start-time (java.util.Date. 1000120000)}]
          
          ;; GitLab Issue ID でフィルタ
          issue-123-entries (time-entry/filter-time-entries 
                             entries {::time-entry/gitlab-issue-id 123})
          
          ;; ステータスでフィルタ
          completed-entries (time-entry/filter-time-entries 
                             entries {::time-entry/status :completed})
          
          ;; 複合条件
          issue-123-completed (time-entry/filter-time-entries 
                               entries {::time-entry/gitlab-issue-id 123
                                        ::time-entry/status :completed})]
      
      (is (= 2 (count issue-123-entries)))
      (is (= #{1 3} (set (map ::time-entry/entry-id issue-123-entries))))
      
      (is (= 2 (count completed-entries)))
      (is (= #{2 3} (set (map ::time-entry/entry-id completed-entries))))
      
      (is (= 1 (count issue-123-completed)))
      (is (= 3 (::time-entry/entry-id (first issue-123-completed)))))))

;; 日次サマリーのビジネスロジックテスト
(deftest summary-test
  (testing "summarize-daily-entries の集計"
    (let [entries [{::time-entry/duration-minutes 30}
                   {::time-entry/duration-minutes 45}
                   {::time-entry/duration-minutes 15}]
          date (java.util.Date.)
          summary (time-entry/summarize-daily-entries entries date)]
      
      (is (= date (::time-entry/date summary)))
      (is (= 90 (::time-entry/total-duration-minutes summary)))  ; 30+45+15
      (is (= 3 (::time-entry/entries-count summary))))))
