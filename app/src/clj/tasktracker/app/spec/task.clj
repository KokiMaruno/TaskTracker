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
                ::last-synced-at]))

;; ローカルメモの更新
(s/def ::task-memo-update-request
  (s/keys :req [::task-id ::local-memo]))

;; タスククローズ要求
(s/def ::task-close-request
  (s/keys :req [::task-id]))

;; タスク検索・フィルタ用
(s/def ::task-filter
  (s/keys :opt [::status
                ::project-name
                ::assignee
                ::priority
                ::labels]))

;; バリデーション用関数
(defn valid-task?
  "タスクが有効かどうかを総合的にチェック"
  [task]
  (and (s/valid? ::task task)
       (if-let [last-synced (::last-synced-at task)]
         (.before last-synced (::update-at task))
         true)))
