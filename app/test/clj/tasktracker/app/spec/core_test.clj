(ns tasktracker.app.spec.core-test
  "core spec のテスト"
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [tasktracker.app.spec.core :as core]))

;; テスト用カスタムジェネレータ
(def test-generators
  {::core/gitlab-url 
   #(gen/fmap (fn [path] (str "https://gitlab.example.com/" path))
              (gen/string-alphanumeric))})

;; spec check のラッパー（カスタムジェネレータ付き）
(deftest spec-check-test
  (testing "全関数の spec check"
    (let [results (stest/check (stest/enumerate-namespace 'tasktracker.app.spec.core)
                               {:gen test-generators})]
      (doseq [result results]
        (is (-> result :clojure.spec.test.check/ret :result)
            (str "Function " (:sym result) " failed spec check"))))))

;; 必要最小限の example-based テスト（spec では表現できないもののみ）
(deftest business-logic-test
  (testing "normalize-labels の基本動作（GitLabからの想定外データ対応）"
    ;; GitLabが想定外のデータを返した場合の防御的処理
    ;; 実際には発生しない可能性が高いが、外部システム連携なので念のため
    (is (= ["dev" "bug"] 
           (core/normalize-labels ["  dev  " "bug" ""])))
    
    (testing "日本語ラベル対応"
      ;; 日本語文字の処理が正しく動作するかチェック
      (is (= ["バグ" "機能追加"] 
             (core/normalize-labels ["バグ" " 機能追加 "]))))))

;; テスト実行用ヘルパー
(defn run-core-tests []
  (run-tests 'tasktracker.app.spec.core-test))
