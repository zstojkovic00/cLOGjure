(ns clogjure.index
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.time ZonedDateTime)))

;; HELPERS
(def index-path "resources/indexes/")

(defn tokenize
  "Splits a log line into individual lowercase alphanumeric words."
  [line]
  (let [clean (str/replace (str/lower-case line) #"[^a-z0-9]" " ")]
    (filter not-empty (str/split clean #" +"))))

(defn to-unix-time
  "Converts an ISO8601 timestamp string to Unix time in milliseconds.
   Returns nil if the timestamp cannot be parsed."
  [timestamp]
  (try
    (.toEpochMilli (.toInstant (ZonedDateTime/parse timestamp)))
    (catch Exception _ nil)))

(defn to-index-path
  "Creates index path from log path and index type."
  [log-path
   index-type]
  (let [file (io/file log-path)
        filename (.getName file)
        filename-clean (str/replace filename #"\.[^.]+$" "")]
    (str index-path filename-clean "-" (name index-type) ".idx"))
  )

;; CREATE
(defn create-index
  "Reads a log file line-by-line and returns two sorted in-memory indexes in a single pass
  [inverted-index (word -> vector of byte offsets where each word appears)
  timestamp-index (byte offset -> unix timestamp for each line)]
  "
  [log-path]
  (with-open [rdr (io/reader log-path)]
    (let [indexes
          (reduce
            (fn [[outer-inverted-acc timestamp-acc current-offset] line]
              (let [words (tokenize line)
                    timestamp (to-unix-time (first (str/split line #" ")))
                    line-length (count (.getBytes line))
                    new-offset (+ current-offset line-length 1)

                    updated-timestamp-index (if timestamp
                                              (assoc timestamp-acc current-offset timestamp)
                                              timestamp-acc)

                    updated-inverted-index (reduce
                                             (fn [inner-inverted-acc word]
                                               (update-in inner-inverted-acc [:words word] (fnil conj []) current-offset))
                                             outer-inverted-acc
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
  [log-path
   inverted-index
   timestamp-index]
  (future
    (with-open [w (io/writer (to-index-path log-path :timestamp))]
      (doseq [[offset timestamp] timestamp-index]
        (.write w (str offset " " timestamp "\n")))
      )

    (with-open [w (io/writer (to-index-path log-path :inverted))]
      (doseq [[word offsets] (:words inverted-index)]
        (.write w (str word " " (str/join " " offsets) "\n")))
      )
    )
  )

;; READ
(defn list-indexes
  "Returns all available inverted index files from disk."
  []
  (filter #(str/ends-with? % "-inverted.idx")
          (map #(.getName %)
               (.listFiles (io/file index-path)))
          )
  )

(defn load-inverted-index
  "Loads a previously persisted inverted index from disk into memory."
  [index-path]
  (with-open [rdr (io/reader index-path)]
    {:words (into (sorted-map)
                  (for [line (line-seq rdr)]
                    (let [[word & offsets-string] (str/split line #" ")
                          offsets (mapv #(Long/parseLong %) offsets-string)]
                      [word offsets])))}))

(defn load-timestamp-index
  "Loads a previously persisted timestamp index from disk into memory."
  [index-path]
  (with-open [rdr (io/reader index-path)]
    (into {}
          (for [line (line-seq rdr)]
            (let [
                  [offset timestamp] (str/split line #" ")]
                  [(Long/parseLong offset) (Long/parseLong timestamp)])
            ))
    )
  )

(defn load-index
  "Loads both inverted and timestamp indexes from disk into memory."
  [index-name]
  (let [base-name (str/replace index-name #"-inverted\.idx$" "")]
    [(load-inverted-index (str index-path base-name "-inverted.idx"))
     (load-timestamp-index (str index-path base-name "-timestamp.idx"))
     ]
    )
  )

(def get-or-create-index
  "Memoized function that implements lazy index loading.

   1. Checks memory (via memoization) - returns immediately if already loaded
   2. Checks disk - loads from disk if index files exist
   3. Builds from log file - creates new index and persists to disk asynchronously if not found

   Returns [inverted-index timestamp-index]."
  (memoize
    (fn [log-path]
      (let [inverted-path (to-index-path log-path :inverted)
            index-name (.getName (io/file inverted-path))]
        (if (.exists (io/file inverted-path))
          (load-index index-name)
          (let [[inverted-index timestamp-index] (create-index log-path)]
            (persist-index-async log-path inverted-index timestamp-index)
            [inverted-index timestamp-index]))
        )
      )
    )
  )