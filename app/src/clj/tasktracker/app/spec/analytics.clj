(ns tasktracker.app.spec.analytics
  "分析・集計関連のspec定義"
  (:require [clojure.spec.alpha :as s]
            [tasktracker.app.spec.core :as core]))

;; 基本フィールド定義
(s/def ::project-name ::core/project-name)
(s/def ::label ::core/label)
(s/def ::date ::core/timestamp)
(s/def ::week-start-date ::core/timestamp)
(s/def ::month (s/and string? #(re-matches #"\d{4}-\d{2}" %)))
(s/def ::total-duration-minutes ::core/duration-minutes)
(s/def ::percentage number?)
(s/def ::task-count pos-int?)

;; プロジェクト別分析
(s/def ::project-time-summary
  (s/keys :req [::project-name
                ::total-duration-minutes
                ::percentage]
          :opt [::task-count]))

(s/def ::project-summaries (s/coll-of ::project-time-summary))

;; ラベル別分析
(s/def ::label-time-summary
  (s/keys :req [::label
                ::total-duration-minutes
                ::percentage]
          :opt [::task-count]))
(s/def ::label-summaries (s/coll-of ::label-time-summary))

;; 日次分析
(s/def ::daily-analysis
  (s/keys :req [::date
                ::total-duration-minutes]
          :opt [::project-summaries
                ::label-summaries]))
(s/def ::daily-analyses (s/coll-of ::daily-analysis))

;; 週次分析
(s/def ::weekly-analysis
  (s/keys :req [::week-start-date
                ::total-duration-minutes]
          :opt [::project-summaries
                ::label-summaries
                ::daily-analyses]))

(s/def ::weekly-analyses (s/coll-of ::weekly-analysis))

;; 月次分析
(s/def ::monthly-analysis
  (s/keys :req [::month
                ::total-duration-minutes]
          :opt [::project-summaries
                ::label-summaries
                ::weekly-analyses]))

;; ユーティリティ関数
(defn calculate-percentage
  "全体時間に対する割合を計算"
  [partial-duration total-duration]
  (if (zero? total-duration)
    0
    (double (/ (* partial-duration 100) total-duration))))
(s/fdef calculate-percentage
  :args (s/cat :partial-duration ::core/duration-minutes
               :total-duration-minutes ::total-duration-minutes)
  :ret number?)
