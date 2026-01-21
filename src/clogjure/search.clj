(ns clogjure.search
  (:require [clogjure.index :as idx]
            [clojure.set :as set]))

(defn tf
  "Calculates Term Frequency for a word in a log line.
   Returns count of occurrences."
  [offsets offset]
  (count (filter #(= % offset) offsets)))

(defn idf
  "Calculates Inverse Document Frequency - how rare a word is.
   Returns log(total-lines / lines-with-word)."
  [total-lines lines-with-word]
  (Math/log (/ total-lines lines-with-word)))

(defn tf-idf
  "Calculates TF-IDF relevance score.
   Returns tf * idf, higher score = more relevant result."
  [tf idf]
  (* tf idf))

(defn intersection
  "Finds intersection of multiple offset vectors.
   Returns vector of offsets present in all input vectors."
  [offsets]
  (if (empty? offsets)
    []
    (let [offset-sets (map set offsets)]
      (vec (apply set/intersection offset-sets)))))

(defn union
  "Finds distinct union of multiple offset vectors
  Returns vector of unique offsets for all input vectors"
  [offsets]
  (if (empty? offsets)
    []
    (let [offsets-set (map set offsets)]
      (vec (apply set/union offsets-set)))))

(defn by-and-words
  "Searches log by multiple words by AND logic with optional time filter.
   Returns vector of {:offset :score :line} maps ranked by TF-IDF."
  [words log-path from to]
  (let [[inverted-index timestamp-index] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to) (idx/get-timestamp-offsets timestamp-index from to))
        word-offsets (map (fn [word] (idx/get-inverted-offsets inverted-index word)) words)
        all-offsets (if timestamp-offsets (cons timestamp-offsets word-offsets) word-offsets)
        intersected-offsets (intersection all-offsets)]
    (if (empty? intersected-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            lines-with-words intersected-offsets
            lines (idx/load-index-lines lines-with-words log-path)
            idf-score (idf total-lines (count lines-with-words))]
        (mapv
          (fn [{:keys [offset line]}]
            (let [tf-score (tf intersected-offsets offset)]
              {:offset offset
               :score  (tf-idf tf-score idf-score)
               :line   line}))
          lines)))))

(defn by-prefix-words
  "Searches log by multiple words that starts with given prefixes and optional time filter
  Returns vector of {:offset :score :line} maps ranked by TF-IDF."
  [prefixes log-path from to]
  (let [[inverted-index timestamp-index] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to) (idx/get-timestamp-offsets timestamp-index from to))
        matching-words (mapcat (fn [prefix]
                                 (take-while (fn [word] (.startsWith word prefix))
                                             (map key (subseq (:words inverted-index) >= prefix))))
                               prefixes)
        word-offsets (map (fn [word] (idx/get-inverted-offsets inverted-index word)) matching-words)
        union-offsets (union word-offsets)
        intersected-offsets (intersection (if timestamp-offsets [union-offsets timestamp-offsets] [union-offsets]))]
    (if (empty? intersected-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            lines-with-words intersected-offsets
            lines (idx/load-index-lines lines-with-words log-path)
            idf-score (idf total-lines (count lines-with-words))]
        (mapv
          (fn [{:keys [offset line]}]
            (let [tf-score (tf intersected-offsets offset)]
              {:offset offset
               :score  (tf-idf tf-score idf-score)
               :line   line}))
          lines)))))

(defn by-or-words
  "Searches log by multiple words OR logic with optional time filter
   Returns vector of {:offset :score :line} maps ranked by TF-IDF."
  [words log-path from to]
  (let [[inverted-index timestamp-index] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to) (idx/get-timestamp-offsets timestamp-index from to))
        word-offsets (map (fn [word] (idx/get-inverted-offsets inverted-index word)) words)
        union-offsets (union word-offsets)
        intersected-offsets (intersection (if timestamp-offsets [union-offsets timestamp-offsets] [union-offsets]))]
    (if (empty? intersected-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            lines-with-words intersected-offsets
            lines (idx/load-index-lines lines-with-words log-path)
            idf-score (idf total-lines (count lines-with-words))]
        (mapv
          (fn [{:keys [offset line]}]
            (let [tf-score (tf intersected-offsets offset)]
              {:offset offset
               :score  (tf-idf tf-score idf-score)
               :line   line}))
          lines)))))