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
(s/def ::month string?) ; YYYY-MM形式
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
                 ::updated-at]
           :opt [::end-time
                 ::duration-minutes
                 ::memo])
   ;; ビジネスルール: 完了状態なら end-time と duration-minutes が必須
   #(if (= :completed (::status %))
      (and (some? (::end-time %))
           (some? (::duration-minutes %)))
      true)
   ;; ビジネスルール: end-time がある場合は start-time より後
   #(if (and (::start-time %) (::end-time %))
      (.before (::start-time %) (::end-time %))
      true)))

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

;; 時間記録検索・フィルタ用（3個以上なのでマップ引数）
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

;; ===== 関数定義 =====

(defn calculate-duration
  "開始時間と終了時間から所要時間(分)を計算"
  [start-time end-time]
  (long (/ (- (.getTime end-time) (.getTime start-time)) 60000)))

(s/fdef calculate-duration
  :args (s/cat :start-time ::core/timestamp
               :end-time ::core/timestamp)
  :ret ::core/duration-minutes
  :fn #(.before (-> % :args :start-time) (-> % :args :end-time)))

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

(defn start-time-entry
  "時間記録を開始"
  [request entry-id]
  (let [now (core/current-timestamp)
        {:keys [::gitlab-issue-id ::memo]} request]
    {::entry-id entry-id
     ::gitlab-issue-id gitlab-issue-id
     ::start-time now
     ::status :running
     ::memo (core/normalize-string memo)
     ::created-at now
     ::updated-at now}))

(s/fdef start-time-entry
  :args (s/cat :request ::time-entry-start-request :entry-id ::core/id)
  :ret ::time-entry)

(defn stop-time-entry
  "時間記録を停止"
  [time-entry memo]
  (let [now (core/current-timestamp)
        normalized-memo (core/normalize-string memo)
        duration (calculate-duration (::start-time time-entry) now)]
    (-> time-entry
        (assoc ::end-time now
               ::duration-minutes duration
               ::status :completed
               ::updated-at now)
        (assoc ::memo (or normalized-memo (::memo time-entry))))))

(s/fdef stop-time-entry
  :args (s/cat :time-entry (s/and ::time-entry running-entry?)
               :memo ::core/optional-string)
  :ret (s/and ::time-entry completed-entry?))

(defn update-time-entry
  "時間記録を更新（手動修正）"
  [time-entry update-request]
  (let [now (core/current-timestamp)
        {:keys [::start-time ::end-time ::duration-minutes ::memo]} update-request
        updated-entry (-> time-entry
                          (assoc ::updated-at now)
                          (cond-> start-time (assoc ::start-time start-time)
                                  end-time (assoc ::end-time end-time)
                                  duration-minutes (assoc ::duration-minutes duration-minutes)
                                  memo (assoc ::memo (core/normalize-string memo))))]
    ;; 開始・終了時間がある場合は duration を再計算
    (if (and (::start-time updated-entry) (::end-time updated-entry))
      (assoc updated-entry ::duration-minutes 
             (calculate-duration (::start-time updated-entry) (::end-time updated-entry)))
      updated-entry)))

(s/fdef update-time-entry
  :args (s/cat :time-entry ::time-entry 
               :update-request ::time-entry-update-request)
  :ret ::time-entry)

(defn filter-time-entries
  "時間記録リストをフィルタ条件で絞り込み"
  [entries filter-spec]
  (let [{:keys [::gitlab-issue-id ::status ::start-date ::end-date]} filter-spec
        parse-date #(.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") %)]
    (cond->> entries
      gitlab-issue-id (filter #(= gitlab-issue-id (::gitlab-issue-id %)))
      status (filter #(= status (::status %)))
      start-date (filter #(>= (.getTime (::start-time %)) 
                               (.getTime (parse-date start-date))))
      end-date (filter #(<= (.getTime (::start-time %)) 
                            (.getTime (parse-date end-date)))))))

(s/fdef filter-time-entries
  :args (s/cat :entries (s/coll-of ::time-entry) 
               :filter-spec ::time-entry-filter)
  :ret (s/coll-of ::time-entry))

(defn summarize-daily-entries
  "日次時間記録のサマリーを作成"
  [entries date]
  (let [total-duration (transduce (map ::duration-minutes) + 0 entries)
        entries-count (count entries)]
    {::date date
     ::total-duration-minutes total-duration
     ::entries-count entries-count}))

(s/fdef summarize-daily-entries
  :args (s/cat :entries (s/coll-of (s/and ::time-entry completed-entry?))
               :date ::core/timestamp)
  :ret ::daily-summary)
