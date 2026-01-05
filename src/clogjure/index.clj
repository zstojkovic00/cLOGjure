(ns clogjure.index
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.time ZonedDateTime)))

(def log-path "resources/logs/file.log")
(def log-path-300MB "resources/logs/spark300MB.log")
(def inverted-index-path "resources/logs/inverted.idx")
(def timestamp-index-path "resources/logs/timestamp.idx")

(defn tokenize
  "Splits a log line into individual lowercase nonempty words."
  [line]
  (filter not-empty (str/split (str/lower-case line) #" ")))

(defn to-unix-time
  "Converts an ISO8601 timestamp string to Unix time in milliseconds.
   Returns nil if the timestamp cannot be parsed."
  [timestamp]
  (try
    (.toEpochMilli (.toInstant (ZonedDateTime/parse timestamp)))
    (catch Exception e nil)))

(defn create-index
  "Reads a log file line-by-line and returns two sorted in-memory indexes in a single pass
  [inverted-index (word -> vector of byte offsets where each word appears)
  timestamp-index (byte offset -> unix timestamp for each line)]
  "
  [filepath]
  (with-open [rdr (io/reader filepath)]
    (let [indexes
          (reduce
            (fn [[inverted-acc ts-acc current-offset] line]
              (let [words (tokenize line)
                    timestamp (to-unix-time (first words))
                    line-length (count (.getBytes line))
                    new-offset (+ current-offset line-length 1)

                    updated-timestamp-index (if timestamp
                                              (assoc ts-acc current-offset timestamp)
                                              ts-acc)

                    updated-inverted-index (reduce
                                             (fn [inner-acc word]
                                               (update-in inner-acc [:words word] (fnil conj []) current-offset))
                                             inverted-acc
                                             words)]
                [updated-inverted-index updated-timestamp-index new-offset]))
            [{:words {}} {} 0]
            (line-seq rdr))]

      (let [[inverted-index timestamp-index _] indexes]
        [(assoc inverted-index :words (into (sorted-map) (:words inverted-index)))
         timestamp-index]
        )
      )))

(defn persist-index-async
  "Persists the in-memory indexes to disk asynchronously.
   Returns a future that completes when both indexes have been written to disk."
  [inverted-index
   timestamp-index]
  (future
    (with-open [w (io/writer timestamp-index-path)]
      (doseq [[offset ts] timestamp-index]
        (.write w (str offset "," ts "\n"))))

    (with-open [w (io/writer inverted-index-path)]
      (doseq [[word offsets] (:words inverted-index)]
        (.write w (str word " " (str/join " " offsets) "\n")))))
  )

(defn load-index
  "Loads a previously persisted index from disk into memory"
  []
  (println "Not implemented")
  )

(def get-or-create-index
  "Memoized function that implements lazy index loading

   1. Checks memory (via memoization) - returns immediately if already loaded
   2. Checks disk - loads from disk if index files exist
   3. Builds from log file - creates new index and persists to disk asynchronously if not found

   Returns the inverted index."
  (memoize
    (fn []
      (if (.exists (io/file inverted-index-path))
        (load-index)
        (let [[inverted-index timestamp-index] (create-index log-path)]
          (persist-index-async inverted-index timestamp-index)
          inverted-index)))))