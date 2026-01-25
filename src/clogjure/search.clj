(ns clogjure.search
  (:require [clogjure.index :as idx]
            [clojure.set :as set]))

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
  "Finds distinct union of multiple offset vectors.
   Returns vector of unique offsets from all input vectors."
  [offsets]
  (if (empty? offsets)
    []
    (let [offsets-set (map set offsets)]
      (vec (apply set/union offsets-set)))))

(defn by-and-words
  "Searches log by multiple words using AND logic with optional time filter.
   Returns vector of {:offset :score :line} maps ranked by TF-IDF."
  [words log-path from to page]
  (let [[inverted-index timestamp-index memory-mapped-log] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to) (idx/get-timestamp-offsets timestamp-index from to))
        words-offsets (mapv (fn [word] (idx/get-inverted-offsets inverted-index word)) words)
        word-frequencies (mapv frequencies words-offsets)
        all-offsets (if timestamp-offsets (cons timestamp-offsets words-offsets) words-offsets)

        matching-offsets (intersection all-offsets)         ;; AND logic, intersection between all words offsets and timestamp offsets, line matches if it survives intersection :)

        latest-matching-offsets (take 500 (drop (* page 500) (sort > matching-offsets)))]
    (if (empty? latest-matching-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            idf-score (idf total-lines (count matching-offsets))
            lines (idx/load-index-lines latest-matching-offsets memory-mapped-log)]
        (mapv
         (fn [{:keys [offset line]}]
           {:offset offset
            :score  (tf-idf (apply + (map (fn [word-frequency] (get word-frequency offset 0)) word-frequencies)) idf-score)
            :line   line})
         lines)))))

(defn by-prefix-words
  "Searches log by multiple words starting with given prefixes with optional time filter.
   Returns vector of {:offset :score :line} maps ranked by TF-IDF."
  [prefixes log-path from to page]
  (let [[inverted-index timestamp-index memory-mapped-log] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to) (idx/get-timestamp-offsets timestamp-index from to))
        matching-words (mapcat (fn [prefix]
                                 (take-while (fn [word] (.startsWith word prefix))
                                             (map key (subseq (:words inverted-index) >= prefix))))
                               prefixes)

        words-offsets (mapv (fn [word] (idx/get-inverted-offsets inverted-index word)) matching-words)
        word-frequencies (mapv frequencies words-offsets)

        union-offsets (union words-offsets)                 ;; OR logic, union between all prefix matches offsets, line matches if it contains any word with matching prefix
        matching-offsets (intersection (if timestamp-offsets [union-offsets timestamp-offsets] [union-offsets])) ;; then AND with timestamp offsets

        latest-matching-offsets (take 500 (drop (* page 500) (sort > matching-offsets)))]
    (if (empty? latest-matching-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            idf-score (idf total-lines (count matching-offsets))
            lines (idx/load-index-lines latest-matching-offsets memory-mapped-log)]
        (mapv
         (fn [{:keys [offset line]}]
           {:offset offset
            :score  (tf-idf (apply + (map (fn [word-frequency] (get word-frequency offset 0)) word-frequencies)) idf-score)
            :line   line})
         lines)))))

(defn by-or-words
  "Searches log by multiple words using OR logic with optional time filter.
   Returns vector of {:offset :score :line} maps ranked by TF-IDF."
  [words log-path from to page]
  (let [[inverted-index timestamp-index memory-mapped-log] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to) (idx/get-timestamp-offsets timestamp-index from to))
        words-offsets (mapv (fn [word] (idx/get-inverted-offsets inverted-index word)) words)
        word-frequencies (mapv frequencies words-offsets)

        union-offsets (union words-offsets)                 ;; OR logic, union between all word offsets, line matches if it contains ANY of the search words
        matching-offsets (intersection (if timestamp-offsets [union-offsets timestamp-offsets] [union-offsets])) ;; then AND with timestamp offsets

        latest-matching-offsets (take 500 (drop (* page 500) (sort > matching-offsets)))]
    (if (empty? latest-matching-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            idf-score (idf total-lines (count matching-offsets))
            lines (idx/load-index-lines latest-matching-offsets memory-mapped-log)]
        (mapv
         (fn [{:keys [offset line]}]
           {:offset offset
            :score  (tf-idf (apply + (map (fn [word-frequency] (get word-frequency offset 0)) word-frequencies)) idf-score)
            :line   line})
         lines)))))
