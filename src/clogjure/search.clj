(ns clogjure.search
  (:require [clogjure.state :as state]
            [clojure.string :as str]))

(defn tf
  "Term Frequency - how many times this offset appears in offsets list."
  [offsets offset]
  (count (filter #(= % offset) offsets)))

;; TODO: add validation divided by zero
(defn idf
  "Inverse Document Frequency - how rare a word is across the log."
  [total-lines lines-with-word]
  (Math/log (/ total-lines lines-with-word)))

(defn tf-idf
  "TF-IDF score for a word in a specific line.
   Formula: tf * idf."
  [tf idf]
  (* tf idf))

;; TODO: add validation if word is not found
(defn by-keyword
  "Search exact by keyword, returns TF-IDF score per offset"
  [keyword]
  (let [index @state/current-index
        offsets (get-in index [:words keyword] [])
        total-lines @state/current-index-total-lines
        lines-with-word (distinct offsets)
        idf-score (idf total-lines (count lines-with-word))]
    (map
      (fn [offset]
        (let [tf-score (tf offsets offset)]
          {:offset offset
           :score  (tf-idf tf-score idf-score)}))
      lines-with-word)))

(by-keyword "zstojkovic00")
