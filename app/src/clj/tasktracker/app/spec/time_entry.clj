(ns tasktracker.app.spec.time-entry
  "TimeEntry関連のspec定義"
  (:require [clojure.spec.alpha :as s]
            [tasktracker.app.spec.core :as core]))

;; TimeEntry基本フィールド
(s/def ::entry-id ::core/id)
(s/def ::gitlab-issue-id ::core/gitlab-issue-id)
(s/def ::start-time ::core/timestamp)
(s/def ::end-time ::core/optional-timestamp)
(s/def ::duration-minutes ::core/duration-minutes)
(s/def ::status ::core/time-entry-status)
(s/def ::memo ::core/memo)
(s/def ::created-at ::core/timestamp)
(s/def ::updated-at ::core/timestamp)

;; フィルタ用の日付フィールド
(s/def ::date ::core/timestamp)
(s/def ::entries-count pos-int?)
(s/def ::start-date string?) ; YYYY-MM-DD形式
(s/def ::end-date string?) ; YYYY-MM-DD形式
(s/def ::month string?)
(s/def ::total-duration-minutes ::core/duration-minutes)

;; プロジェクト別・ラベル別集計
(s/def ::project-summaries (s/map-of ::core/project-name ::core/duration-minutes))
(s/def ::label-summaries (s/map-of string? ::core/duration-minutes))

;; TimeEntry完全版
(s/def ::time-entry
  (s/and
   (s/keys :req [::entry-id
                 ::gitlab-issue-id
                 ::start-time
                 ::status
                 ::created-at
                 ::updated-at
                 ]
           :opt [::end-time
                 ::duration-minutes
                 ::memo])
   core/valid-time-range?
   core/valid-duration?))

;; 時間記録開始用
(s/def ::time-entry-start-request
  (s/keys :req [::gitlab-issue-id]
          :opt [::memo]))

;; 時間記録停止用
(s/def ::time-entry-stop-request
  (s/keys :req [::entry-id]
          :opt [::memo]))

;; 時間修正用
(s/def ::manual-update-request
  (s/keys :req [::entry-id]
          :opt [::start-time
                ::end-time
                ::memo]))

;; 時間記録更新用
(s/def ::time-entry-update-request
  (s/keys :req [::entry-id]
          :opt [::start-time
                ::end-time
                ::duration-minutes
                ::memo]))

;; 時間記録検索・フィルタ用
(s/def ::time-entry-filter
  (s/keys :opt [::gitlab-issue-id
                ::status
                ::start-date
                ::end-date]))

;; 集計用のデータ構造
;; 日次
(s/def ::daily-summary
  (s/keys :req [::date
                ::total-duration-minutes
                ::entries-count]
          :opt [::project-summaries
                ::label-summaries]))

(s/def ::daily-summaries (s/coll-of ::daily-summary))

;; 週次
(s/def ::weekly-summary
  (s/keys :req [::total-duration-minutes
                ::daily-summaries]))

(s/def ::weekly-summaries (s/coll-of ::weekly-summary))

;; 月次
(s/def ::monthly-summary
  (s/keys :req [::month
                ::total-duration-minutes
                ::weekly-summaries]))

(defn calculate-duration
  "開始時間と終了時間から所要時間(分)を計算"
  [start-time end-time]
  (/ (- (.getTime end-time) (.getTime start-time)) 60000))

(s/fdef calculate-duration
  :args (s/cat :start-time ::core/timestamp
               :end-time ::core/timestamp)
  :ret ::core/duration-minutes)


(defn format-duration
  "所要時間(分)を人間が読みやすい形式に変換"
  [duration-minutes]
  (let [hours (quot duration-minutes 60)
        minutes (rem duration-minutes 60)]
    (cond
      (zero? hours) (str minutes "分")
      (zero? minutes) (str hours "時間")
      :else (str hours "時間" minutes "分"))))

(s/fdef format-duration
  :args (s/cat :duration-minutes ::core/duration-minutes)
  :ret string?)

(defn running-entry?
  "実行中の時間記録かどうか"
  [time-entry]
  (= (::status time-entry) :running))

(s/fdef running-entry?
  :args (s/cat :time-entry ::time-entry)
  :ret boolean?)

(defn completed-entry?
  "完了した時間記録かどうか"
  [time-entry]
  (and (= (::status time-entry) :completed)
       (some? (::end-time time-entry))
       (some? (::duration-minutes time-entry))))
(s/fdef completed-entry?
  :args (s/cat :time-entry ::time-entry)
  :ret boolean?)

(defn valid-manual-entry?
  "手動入力された時間記録が有効かどうか"
  [manual-request]
  (core/valid-time-range? manual-request))
(s/fdef valid-manual-entry?
  :args (s/cat :manual-request ::manual-update-request)
  :ret boolean?)

