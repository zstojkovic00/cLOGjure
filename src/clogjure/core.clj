(ns clogjure.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.time LocalDateTime ZonedDateTime)
           (java.time.format DateTimeFormatter)))

;; 1. Def paths
(def log-path "resources/logs/file.log")
(def log-path-300MB "resources/logs/spark300MB.log")
(def inverted-index-path "resources/logs/inverted.idx")
(def timestamp-index-path "resources/logs/timestamp.idx")
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

(create-index log-path inverted-index-path "ERROR")

;; 4. Inverted index with byte offset
(defn tokenize [line]
  (filter not-empty (str/split (str/lower-case line) #" "))
  )

(def test-line "2025-12-05T22:17:01.524+01:00  INFO 7605 --- [abstractive-version-control-system-manager] VersionControlSystemManagerApplicationKt : Starting  using Java 21.0.1")
(tokenize test-line)

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

(defn to-unix-time [timestamp-str]
  (try
    (.toEpochMilli (.toInstant (ZonedDateTime/parse timestamp-str)))
    (catch Exception e nil)))

(defn create-indexes-in-memory [filepath]
  (with-open [rdr (io/reader filepath)]
    (reduce (fn [[inverted-acc ts-acc current-offset] line]
              (let [words (tokenize line)
                    timestamp (to-unix-time (first words))
                    line-length (count (.getBytes line))
                    new-offset (+ current-offset line-length 1)

                    updated-timestamp-index (if timestamp
                                              (assoc ts-acc current-offset timestamp)
                                              ts-acc)

                    updated-inverted-index (reduce (fn [inner-acc word]
                                                     (update-in inner-acc [:words word] (fnil conj []) current-offset))
                                                   inverted-acc
                                                   words)]
                [updated-inverted-index updated-timestamp-index new-offset]))

            [{:words {}} {} 0]
            (line-seq rdr)
            )
    )
  )

(defn write-indexes-to-disk [inverted-index-in-memory timestamp-index-in-memory inverted-path timestamp-path]
  (with-open [w (io/writer timestamp-path)]
    (doseq [[offset ts] timestamp-index-in-memory]
      (.write w (str offset "," ts "\n"))))

  (with-open [w (io/writer inverted-path)]
    (doseq [[word offsets] (sort (:words inverted-index-in-memory))]
      (.write w (str word " " (str/join " " offsets) "\n")))))

(defn build-indexes [log-path inverted-path timestamp-path]
  (let [
        [inverted-index-in-memory timestamp-index-in-memory _] (create-indexes-in-memory log-path)]
    (write-indexes-to-disk inverted-index-in-memory timestamp-index-in-memory inverted-path timestamp-path)))

(build-indexes log-path-300MB inverted-index-path timestamp-index-path)