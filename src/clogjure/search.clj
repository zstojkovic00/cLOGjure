(ns clogjure.search
  (:require [clogjure.index :as idx]
            [clogjure.state :as state]
            [clojure.set :as set]))

(defn tf
  "Calculates Term Frequency - counts how many times a word appears in a log line."
  [offsets offset]
  (count (filter #(= % offset) offsets)))

(defn idf
  "Calculates Inverse Document Frequency - measures how rare a word is across the log.
   Formula: log(N / df) where N = total log lines, df = lines containing the word."
  [total-lines lines-with-word]
  (Math/log (/ total-lines lines-with-word)))

(defn tf-idf
  "Calculates TF-IDF score for ranking a word's relevance in a log line.
   Formula: TF × IDF. Higher score = more relevant result."
  [tf idf]
  (* tf idf))

(defn offsets-intersection
  "Finds intersection of offsets for multiple keywords."
  [index keywords]
  (let [offset-sets (map (fn [keyword]
                           (set (get-in index [:words keyword] [])))
                         keywords)]
    (vec (apply set/intersection offset-sets))))

(defn by-exact-keywords
  "Search by multiple keywords, returns lines containing all keywords,
   ranked by TF-IDF score."
  [keywords log-path]
  (let [index @state/current-index
        offsets (offsets-intersection index keywords)]
    (if (empty? offsets)
      (println "No results found.")
      (let [total-lines @state/current-index-total-lines
            lines-with-words (distinct offsets)
            lines (idx/load-index-lines lines-with-words log-path)
            idf-score (idf total-lines (count lines-with-words))]
        (mapv
         (fn [{:keys [offset line]}]
           (let [tf-score (tf offsets offset)]
             {:offset offset
              :score  (tf-idf tf-score idf-score)
              :line   line}))
         lines)))))