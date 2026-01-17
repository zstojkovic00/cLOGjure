(ns clogjure.index
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clogjure.util :as util])
  (:import (java.io RandomAccessFile)))

(def index-path "resources/indexes/")
(def registry-path "resources/indexes/index-registry.idx")

(defn tokenize
  "Splits a log line into individual lowercase alphanumeric words."
  [line]
  (let [clean (str/replace (str/lower-case line) #"[^a-z0-9]" " ")]
    (filter not-empty (str/split clean #" +"))))

(defn to-index-path
  "Creates index path from log path and index type."
  [log-path index-type]
  (let [file (io/file log-path)
        filename (.getName file)
        filename-clean (str/replace filename #"\.[^.]+$" "")]
    (str index-path filename-clean "-" (name index-type) ".idx")))

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
                   timestamp (util/to-unix-timestamp line)
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
         timestamp-index]))))

(defn persist-index-async
  "Persists in-memory indexes to disk asynchronously.
   Returns a future that completes when indexes and registry have been written."
  [log-path inverted-index timestamp-index]
  (let [index-name (-> (to-index-path log-path :inverted) io/file .getName)]
    (future
      (with-open [w (io/writer (to-index-path log-path :timestamp))]
        (doseq [[offset timestamp] timestamp-index]
          (.write w (str offset " " timestamp "\n"))))

      (with-open [w (io/writer (to-index-path log-path :inverted))]
        (doseq [[word offsets] (:words inverted-index)]
          (.write w (str word " " (str/join " " offsets) "\n"))))

      ;; TODO: proveri da li postoji log u registry-path pre append-a
      (with-open [w (io/writer registry-path :append true)]
        (.write w (str index-name " " log-path "\n"))))))

(defn list-indexes
  "Returns all available inverted index files from disk."
  []
  (filter #(str/ends-with? % "-inverted.idx")
          (map #(.getName %)
               (.listFiles (io/file index-path)))))

(defn list-registry
  "Returns all available index names from disk."
  []
  (if (.exists (io/file registry-path))
    (with-open [rdr (io/reader registry-path)]
      (into {}
            (for [line (line-seq rdr)]
              (let [[index-name log-path] (str/split line #" " 2)]
                [index-name log-path]))))
    {}))

(defn load-log-path
  "Loads log path for given index name from registry."
  [index-name]
  (get (list-registry) index-name))

(defn load-inverted-index
  "Loads inverted index from disk into memory."
  [index-path]
  (with-open [rdr (io/reader index-path)]
    {:words (into (sorted-map)
                  (for [line (line-seq rdr)]
                    (let [[word & offsets-string] (str/split line #" ")
                          offsets (mapv #(Long/parseLong %) offsets-string)]
                      [word offsets])))}))

(defn load-timestamp-index
  "Loads timestamp index from disk into memory."
  [index-path]
  (with-open [rdr (io/reader index-path)]
    (into {}
          (for [line (line-seq rdr)]
            (let [[offset timestamp] (str/split line #" ")]
              [(Long/parseLong offset) (Long/parseLong timestamp)])))))

(defn load-index
  "Loads inverted and timestamp indexes from disk into memory."
  [log-path]
  (let [inverted-index (load-inverted-index (to-index-path log-path :inverted))
        timestamp-index (load-timestamp-index (to-index-path log-path :timestamp))]
    [inverted-index timestamp-index]))

(def load-or-create-index
  "Memoized function that implements lazy index loading.

   1. Checks memory (via memoization) - returns immediately if already loaded
   2. Checks disk - loads from disk if index files exist
   3. Builds from log file - creates new index and persists to disk asynchronously if not found."
  (memoize
   (fn [log-path]
     (let [inverted-path (to-index-path log-path :inverted)]
       (if (.exists (io/file inverted-path))
         (load-index log-path)
         (let [[inverted-index timestamp-index] (create-index log-path)]
           (persist-index-async log-path inverted-index timestamp-index)
           [inverted-index timestamp-index]))))))

(defn load-index-lines
  ""
  [offsets log-path]
  (with-open [raf (RandomAccessFile. ^String log-path "r")]
    (mapv
     (fn [offset]
       (.seek raf offset)
       {:offset offset
        :line   (.readLine raf)})
     offsets)))
