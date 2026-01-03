(ns clogjure.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.time LocalDateTime ZonedDateTime)
           (java.time.format DateTimeFormatter)))

;; 1. Def paths
(def log-path "resources/logs/file.log")
(def index-path "resources/logs/file-index.log")
;;(def big-log-path "resources/spark300MB.log")

;; 2. Naive implementation
(System/getProperty "user.dir")

(defn read-logs [filepath] (str/split-lines (slurp filepath)))
(read-logs log-path)

(defn search-by-level [logs level]
  (reduce (fn [acc line]
            (if (str/includes? line level)
              (conj acc line)
              acc))
          []
          logs))

(search-by-level (read-logs log-path) "WARN")
(search-by-level (read-logs log-path) "INFO")

(LocalDateTime/now)

(defn write-log [filepath level msg]
  (let [timestamp (.format (LocalDateTime/now)
                           (DateTimeFormatter/ofPattern "yyyy-MM-ddTHH:mm:ss"))
        line (str timestamp " " level " " msg "\n")]
    (spit filepath line :append true)))

;;(write-log log-path "ERROR" "Nesto")
(search-by-level (read-logs log-path) "ERROR")

;; 3. Buffer reader - streaming
(defn create-index [filepath indexpath level]
  (with-open [rdr (io/reader filepath)
              w (io/writer indexpath)]
    (doseq [[index line] (map-indexed vector (line-seq rdr))]
      (if (str/includes? line level)
        (.write w (str "line in original file: " index "\n" line "\n"))))))

(create-index log-path index-path "ERROR")

;; 4. Inverted index with byte offset
(defn tokenize [line]
  (filter not-empty (str/split (str/lower-case line) #" "))
  )

(def test-line "2025-12-05T22:17:01.524+01:00  INFO 7605 --- [abstractive-version-control-system-manager] VersionControlSystemManagerApplicationKt : Starting  using Java 21.0.1")
(tokenize test-line)

(defn create-inverted-index [filepath indexpath]
  (with-open [rdr (io/reader filepath)
              w (io/writer indexpath)]
    (doseq [line (line-seq rdr)]
      (tokenize line)
      )
    )
  )


;; napravimo reduce koji prima funckiju sa parametrom mapa i line, to je ulaz
;; body je onda da za te reci uradimo update-index znaci i nad update-index radimo reduce koji prima idx i word
(create-inverted-index log-path index-path)

;; Index structure, practice
(def dictionary {:words {;; byte-offset timestamp
                         "error"  [[0 1704067200] [500 1704067505]]
                         "memory" [[0 1704067200] [200 1704067300]]
                         }})

(identity dictionary)
(update-in dictionary [:words "test"] conj [0 1704067200])

(conj [] "prvi")
(conj nil "prvi")
;; ako je kolekcija nil vraca listu a ne vektor

;;(filter (fn [word] (> (count word) 3)) (tokenize test-line))
;;(filter not-empty (str/split test-line  #" "))

(defn count-lines [filepath]
  (with-open [rdr (io/reader filepath)]
    (reduce (fn [count line]
              (inc count))
            0
            (line-seq rdr))))

(count-lines log-path)

;; skupi sve linije u vektor umesto sto samo brojis

(defn add-lines [filepath]
  (with-open [rdr (io/reader filepath)]
    (reduce (fn [acc line]
              (conj acc line)
              )
            []
            (line-seq rdr)
            )
    )
  )

(add-lines log-path)

(def map1 [])
(conj map1 "2025-12-05T22:17:01.524+01:00  INFO 7605")

(defn filter-by-level [filepath level]
  (with-open [rdr (io/reader filepath)]
    (reduce (fn [acc line]
              (if (str/includes? line level)
                (conj acc line)
                )
              )
            []
            (line-seq rdr)
            )
    )
  )

(filter-by-level log-path "ERROR")
(str/includes? "ERRORNESTO" "ERROR")

(defn count-words [filepath]
  (with-open [rdr (io/reader filepath)]
    (reduce (fn [outer-acc line]
              (let [words (str/split line #" ")]
                (reduce (fn [inner-acc word]
                          (update-in inner-acc [:words word] (fnil inc 0))
                          )
                        outer-acc
                        words
                        )
                )
              )
            {}
            (line-seq rdr)
            )
    )
  )

(count-words log-path)

(def dictionary {:words {;; byte-offset timestamp
                         "error"  []
                         "memory" []
                         }})

(update-in dictionary [:words "error"] (fnil conj []) [0 1704067200])

(defn to-unix-time [timestamp]
  (.toEpochMilli (.toInstant (ZonedDateTime/parse timestamp))))

;; byte offset i timestamp
(defn create-inverted-index [filepath indexpath]
  (with-open [rdr (io/reader filepath)]
    (reduce (fn [[outer-acc current-offset] line]
              (let [
                    words (tokenize line)
                    unix-timestamp (to-unix-time (first words))
                    line-length (count (.getBytes line))
                    new-offset (+ current-offset line-length 1)
                    updated-map (reduce (fn [inner-acc word]
                                          (update-in inner-acc [:words word] (fnil conj []) current-offset unix-timestamp))
                                        outer-acc
                                        words)]
                [updated-map new-offset]))
            [{:words {}} 0]
            (line-seq rdr)
            )
    )
  )

(create-inverted-index log-path index-path)