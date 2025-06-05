(ns tasktracker.app.spec.task
  "Task関連のspec定義"
  (:require [clojure.spec.alpha :as s]
            [tasktracker.app.spec.core :as core]))

;; Task基本フィールド
(s/def ::task-id ::core/id)
(s/def ::gitlab-issue-id ::core/gitlab-issue-id)
(s/def ::gitlab-project-id ::core/gitlab-project-id)
(s/def ::title ::core/title)
(s/def ::description ::core/description)
(s/def ::status ::core/task-status)
(s/def ::project-name ::core/project-name)
(s/def ::labels ::core/labels)
(s/def ::priority ::core/priority)
(s/def ::assignee ::core/assignee)
(s/def ::gitlab-url ::core/gitlab-url)

;; TaskTracker固有フィールド
(s/def ::local-memo ::core/memo)
(s/def ::is-synced boolean?)
(s/def ::last-synced-at ::core/optional-timestamp)
(s/def ::created-at ::core/timestamp)
(s/def ::updated-at ::core/timestamp)

;; Gitlabから取得するタスク
(s/def ::gitlab-task
  (s/keys :req [::gitlab-issue-id
                ::gitlab-project-id
                ::title
                ::status
                ::project-name]
          :opt [::description
                ::labels
                ::priority
                ::assignee
                ::gitlab-url]))

;; ローカルで管理するタスク情報
(s/def ::task
  (s/and
   (s/keys :req [::task-id
                 ::gitlab-issue-id
                 ::gitlab-project-id
                 ::title
                 ::status
                 ::project-name
                 ::is-synced
                 ::created-at
                 ::updated-at]
           :opt [::description
                 ::labels
                 ::priority
                 ::assignee
                 ::gitlab-url
                 ::local-memo
                 ::last-synced-at])
   ;; ビジネスルール: last-synced-atがある場合、updated-atより前である必要
   #(if-let [last-synced (::last-synced-at %)]
      (.before last-synced (::updated-at %))
      true)))

;; ローカルメモの更新用リクエスト
(s/def ::task-memo-update-request
  (s/keys :req [::task-id ::local-memo]))

;; タスククローズ要求
(s/def ::task-close-request
  (s/keys :req [::task-id]))

;; タスク検索・フィルタ用（3個以上なのでマップ引数）
(s/def ::task-filter
  (s/keys :opt [::status
                ::project-name
                ::assignee
                ::priority
                ::labels]))

;; ===== 関数定義 =====

(defn valid-task?
  "タスクが有効かどうかを総合的にチェック"
  [task]
  (s/valid? ::task task))

(s/fdef valid-task?
  :args (s/cat :task (s/keys :req [::task-id] :opt [::last-synced-at ::updated-at]))
  :ret boolean?)

(defn create-task-from-gitlab
  "GitLabタスクからローカルタスクを作成"
  [gitlab-task task-id]
  (let [now (core/current-timestamp)]
    (-> gitlab-task
        (assoc ::task-id task-id
               ::is-synced true
               ::created-at now
               ::updated-at now
               ::last-synced-at now)
        (update ::labels core/normalize-labels)
        (update ::title core/normalize-string)
        (update ::description core/normalize-string))))

(s/fdef create-task-from-gitlab
  :args (s/cat :gitlab-task ::gitlab-task :task-id ::core/id)
  :ret ::task)

(defn update-task-memo
  "タスクのローカルメモを更新"
  [task memo]
  (let [normalized-memo (core/normalize-string memo)]
    (-> task
        (assoc ::local-memo normalized-memo
               ::updated-at (core/current-timestamp)))))

(s/fdef update-task-memo
  :args (s/cat :task ::task :memo ::core/optional-string)
  :ret ::task)

(defn sync-task-from-gitlab
  "GitLabからの最新情報でタスクを同期"
  [existing-task gitlab-task]
  (let [now (core/current-timestamp)]
    (-> existing-task
        (merge (select-keys gitlab-task [::title ::description ::status 
                                         ::labels ::priority ::assignee 
                                         ::gitlab-url]))
        (assoc ::is-synced true
               ::updated-at now
               ::last-synced-at now)
        (update ::labels core/normalize-labels)
        (update ::title core/normalize-string)
        (update ::description core/normalize-string))))

(s/fdef sync-task-from-gitlab
  :args (s/cat :existing-task ::task :gitlab-task ::gitlab-task)
  :ret ::task)

(defn mark-task-as-unsynced
  "タスクを未同期状態にマーク"
  [task]
  (-> task
      (assoc ::is-synced false
             ::updated-at (core/current-timestamp))))

(s/fdef mark-task-as-unsynced
  :args (s/cat :task ::task)
  :ret ::task)

(defn task-needs-sync?
  "タスクが同期を必要とするかどうかを判定"
  [task sync-threshold-minutes]
  (or (not (::is-synced task))
      (when-let [last-synced (::last-synced-at task)]
        (let [now (core/current-timestamp)
              threshold-ms (* sync-threshold-minutes 60 1000)]
          (> (- (.getTime now) (.getTime last-synced)) threshold-ms)))))

(s/fdef task-needs-sync?
  :args (s/cat :task ::task :sync-threshold-minutes ::core/duration-minutes)
  :ret boolean?)

(defn filter-tasks
  "タスクリストをフィルタ条件で絞り込み"
  [tasks filter-spec]
  (let [{:keys [::status ::project-name ::assignee ::priority ::labels]} filter-spec]
    (cond->> tasks
      status       (filter #(= status (::status %)))
      project-name (filter #(= project-name (::project-name %)))
      assignee     (filter #(= assignee (::assignee %)))
      priority     (filter #(= priority (::priority %)))
      labels       (filter #(some (set (::labels %)) labels)))))

(s/fdef filter-tasks
  :args (s/cat :tasks (s/coll-of ::task) :filter-spec ::task-filter)
  :ret (s/coll-of ::task))

(defn task-summary
  "タスクの基本情報サマリーを作成"
  [task]
  (select-keys task [::task-id ::gitlab-issue-id ::title ::status 
                     ::project-name ::priority ::is-synced]))

(s/fdef task-summary
  :args (s/cat :task ::task)
  :ret (s/keys :req [::task-id ::gitlab-issue-id ::title ::status 
                     ::project-name ::priority ::is-synced]))
