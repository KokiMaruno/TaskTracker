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
(s/def ::non-zero-duration (s/and ::duration-minutes pos?))
(s/def ::priority #{:low :medium :high :urgent})

;; メモ・説明
(s/def ::memo ::optional-string)
(s/def ::description ::optional-string)
(s/def ::title ::non-empty-string)

;; プロジェクト関連
(s/def ::project-name ::non-empty-string)
(s/def ::assignee ::optional-string)

;; 時間範囲用のフィールド
(s/def ::start-time ::timestamp)
(s/def ::end-time ::timestamp)

;; 時間範囲バリデーション用のspec
(s/def ::time-range-request
  (s/keys :req [::start-time ::end-time]))

(s/def ::valid-time-range
  (s/and ::time-range-request
         #(.before (::start-time %) (::end-time %))))

;; バリデーション用ヘルパー関数
(defn valid-time-range?
  "開始時間が終了時間より前であることを確認"
  [time-range]
  (.before (::start-time time-range) (::end-time time-range)))

(s/fdef valid-time-range?
  :args (s/cat :time-range ::time-range-request)
  :ret boolean?)

;; 時間範囲の長さ計算
(defn time-range-duration-minutes
  "時間範囲から分単位の長さを計算"
  [time-range]
  (let [start (::start-time time-range)
        end (::end-time time-range)]
    (long (/ (- (.getTime end) (.getTime start)) 60000))))

(s/fdef time-range-duration-minutes
  :args (s/cat :time-range ::valid-time-range)
  :ret ::duration-minutes)

;; 現在時刻の取得
(defn current-timestamp
  "現在時刻をjava.util.Dateで取得"
  []
  (java.util.Date.))

(s/fdef current-timestamp
  :args (s/cat)
  :ret ::timestamp)

;; 文字列の空白除去とnormalization
(defn normalize-string
  "文字列の前後空白を除去し、空文字列の場合はnilを返す"
  [s]
  (when s
    (let [trimmed (str/trim s)]
      (when-not (str/blank? trimmed)
        trimmed))))

(s/fdef normalize-string
  :args (s/cat :s (s/nilable string?))
  :ret ::optional-string)

;; ラベルのnormalization
(defn normalize-labels
  "ラベル配列から空文字列を除去"
  [labels]
  (->> labels
       (map normalize-string)
       (filter some?)
       (vec)))

(s/fdef normalize-labels
  :args (s/cat :labels (s/coll-of (s/nilable string?)))
  :ret ::labels)
