(ns tasktracker.app.spec.core
  "共通的なspec定義"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;; 基本的なデータ型
(s/def ::id pos-int?)
(s/def ::uuid string?)
(s/def ::non-empty-string (s/and string? (complement str/blank?)))
(s/def ::optional-string (s/nilable string?))

;; 日時関連
(s/def ::timestamp inst?)
(s/def ::optional-timestamp (s/nilable inst?))


;; Gitlab関連
(s/def ::gitlab-issue-id pos-int?)
(s/def ::gitlab-project-id pos-int?)
(s/def ::gitlab-url (s/and string? #(str/starts-with? % "http")))

;; ステータス定義
(s/def ::task-status #{:open :in-progress :closed :blocked})
(s/def ::time-entry-status #{:running :completed})

;; ラベル関連
(s/def ::label string?)
(s/def ::labels (s/coll-of string? :kind vector? :min-count 0))

;; 時間関連
(s/def ::duration-minutes (s/and int? #(>= % 0)))
(s/def ::priority #{:low :medium :high :urgent})

;; メモ・説明
(s/def ::memo ::optional-string)
(s/def ::description ::optional-string)
(s/def ::title ::non-empty-string)

;; バリデーション用ヘルパー関数
(defn valid-time-range?
  "開始時間が終了時間より前であることを確認"
  [{:keys [start-time end-time]}]
  (if (and start-time end-time)
    (.before start-time end-time)
    true))
(s/fdef valid-time-range?
  :args (s/cat :time-map (s/keys :req-un [::start-time ::end-time]))
  :ret boolean?)

(defn valid-duration?
  "duration-minutesが開始・終了時間と整合していることを確認"
  [{:keys [start-time end-time duration-minutes]}]
  (if (and start-time end-time duration-minutes)
    (let [calculated-minutes (/ (- (.getTime end-time) (.getTime start-time)) 60000)]
      (> (Math/abs (- calculated-minutes duration-minutes)) 1))
    true))
(s/fdef valid-duration?
  :args (s/cat :time-map (s/keys ::req-un [::start-time ::end-time ::duration-minutes]))
  :ret boolean?)

;; プロジェクト関連
(s/def ::project-name ::non-empty-string)
(s/def ::assignee ::optional-string)
