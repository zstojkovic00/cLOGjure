(ns clogjure.index
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clogjure.util :as util])
  (:import (java.io RandomAccessFile)))

(def index-path "resources/indexes/")
(def registry-path "resources/indexes/index-registry.idx")

(defn split-line-by-timestamp
  "Splits log line by timestamp.
   Returns vector of unix-timestamp and content or nil line if no timestamp found."
  [line]
  (let [matched (some (fn [{:keys [regex formatter]}]
                        (let [match (re-find regex line)]
                          (when match
                            {:match match :formatter formatter})))
                      util/dt-formatters)]
    (if matched
      (let [unix-timestamp (util/try-parse-timestamp (:match matched) (:formatter matched))
            content (str/trim (subs line (count (:match matched))))]
        [unix-timestamp content])
      [nil line])))

(defn tokenize
  "Splits a log line into lowercase alphanumeric words.
   Returns vector of strings."
  [line]
  (let [clean (str/replace (str/lower-case line) #"[^a-z0-9]" " ")]
    (filter not-empty (str/split clean #" +"))))

(defn create-index
  "Parses log file line-by-line.
  Returns two sorted in-memory indexes in a single pass
  inverted-index (word -> vector of byte offsets where each word appears)
  timestamp-index (byte offset -> unix timestamp for each line)
  "
  [log-path]
  (with-open [rdr (io/reader log-path)]
    (let [indexes
          (reduce
           (fn [[outer-inverted-acc timestamp-acc current-offset] line]
             (let [[unix-timestamp content] (split-line-by-timestamp line)
                   words (tokenize content)
                   line-length (count (.getBytes line))
                   new-offset (+ current-offset line-length 1)

                   updated-timestamp-index (if unix-timestamp
                                             (assoc timestamp-acc current-offset unix-timestamp)
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

(defn to-index-path
  "Creates index file path from log path and index type.
   Returns string path."
  [log-path index-type]
  (let [file (io/file log-path)
        filename (.getName file)
        filename-clean (str/replace filename #"\.[^.]+$" "")]
    (str index-path filename-clean "-" (name index-type) ".idx")))

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

      (let [registry (list-registry)]
        (if (nil? (get registry index-name))
          (with-open [w (io/writer registry-path :append true)]
            (.write w (str index-name " " log-path "\n"))))))))

(defn list-indexes
  "Lists all available inverted index files from disk.
   Returns vector of index filenames."
  []
  (filter #(str/ends-with? % "-inverted.idx")
          (map #(.getName %)
               (.listFiles (io/file index-path)))))

(defn list-registry
  "Lists all index entries from registry file.
   Returns map of index-name to log-path."
  []
  (if (.exists (io/file registry-path))
    (with-open [rdr (io/reader registry-path)]
      (into {}
            (for [line (line-seq rdr)]
              (let [[index-name log-path] (str/split line #" " 2)]
                [index-name log-path]))))
    {}))

(defn load-log-path
  "Loads log path for given index name from registry.
   Returns string path or nil."
  [index-name]
  (get (list-registry) index-name))

(defn load-inverted-index
  "Loads inverted index from disk into memory.
   Returns map of words to byte offsets."
  [index-path]
  (with-open [rdr (io/reader index-path)]
    {:words (into (sorted-map)
                  (for [line (line-seq rdr)]
                    (let [[word & offsets-string] (str/split line #" ")
                          offsets (mapv #(Long/parseLong %) offsets-string)]
                      [word offsets])))}))

(defn load-timestamp-index
  "Loads timestamp index from disk into memory.
   Returns map of byte offsets to unix timestamps."
  [index-path]
  (with-open [rdr (io/reader index-path)]
    (into {}
          (for [line (line-seq rdr)]
            (let [[offset timestamp] (str/split line #" ")]
              [(Long/parseLong offset) (Long/parseLong timestamp)])))))

(defn get-inverted-offsets
  "Returns vector of byte offsets for a word from index.
   Returns empty vector if word not found."
  [index word]
  (get-in index [:words (str/lower-case word)] []))

(defn get-timestamp-offsets
  "Returns all offsets within time range [from, to]."
  [timestamp-index from to]
  (vec (for [[offset ts] timestamp-index
             :when (and (or (nil? from) (>= ts from))
                        (or (nil? to) (<= ts to)))]
         offset)))

(defn load-index
  "Loads indexes from disk.
   Returns vector of [inverted-index timestamp-index]."
  [log-path]
  (let [inverted-index (load-inverted-index (to-index-path log-path :inverted))
        timestamp-index (load-timestamp-index (to-index-path log-path :timestamp))]
    [inverted-index timestamp-index]))

(def load-or-create-index
  "Memoized function that implements lazy index loading.
    1. Checks memory (via memoization) - returns immediately if already loaded
    2. Checks disk - loads from disk if index files exist
    3. Builds from log file - creates new index and persists to disk asynchronously if not found.

   Returns vector of [inverted-index timestamp-index].
   "
  (memoize
   (fn [log-path]
     (let [inverted-path (to-index-path log-path :inverted)]
       (if (.exists (io/file inverted-path))
         (load-index log-path)
         (let [[inverted-index timestamp-index] (create-index log-path)]
           (persist-index-async log-path inverted-index timestamp-index)
           [inverted-index timestamp-index]))))))

;; TODO: Memory mapping
(defn load-index-lines
  "Reads lines from file at given byte offsets.
   Returns vector of {:offset :line} maps."
  [offsets log-path]
  (with-open [raf (RandomAccessFile. ^String log-path "r")]
    (mapv
     (fn [offset]
       (.seek raf offset)
       {:offset offset
        :line   (.readLine raf)})
     offsets)))