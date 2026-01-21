(ns clogjure.index-test
  (:require [clogjure.index :as idx]
            [clogjure.util :as util]
            [midje.sweet :refer :all]))

(facts "split-line-by-timestamp -> tokenize flow"
       (fact "Spring Boot log line"
             (let [line "2025-12-05T22:17:01.524+01:00 INFO 7605 --- server started"
                   [unix-timestamp content] (idx/split-line-by-timestamp line)]
               unix-timestamp => integer?
               content => "INFO 7605 --- server started"
               (idx/tokenize content) => ["info" "7605" "server" "started"]))
       (fact "European format log line"
             (let [line "14.01.2026 00:00:00.005 [scheduler] INFO task completed"
                   [unix-timestamp content] (idx/split-line-by-timestamp line)]
               unix-timestamp => integer?
               content => "[scheduler] INFO task completed"
               (idx/tokenize content) => ["scheduler" "info" "task" "completed"]))
       (fact "line without timestamp"
             (let [line "SLF4J: Class path contains multiple bindings"
                   [unix-timestamp content] (idx/split-line-by-timestamp line)]
               unix-timestamp => nil
               content => "SLF4J: Class path contains multiple bindings"
               (idx/tokenize content) => ["slf4j" "class" "path" "contains" "multiple" "bindings"])))

(facts "to-index-path, returns index file path from log path and index type"
       (fact "inverted index path"
             (idx/to-index-path "resources/logs/file.log" :inverted)
             => "resources/indexes/file-inverted.idx")
       (fact "timestamp index path"
             (idx/to-index-path "resources/log/file.log" :timestamp)
             => "resources/indexes/file-timestamp.idx"))

(facts "create-index, parses log file and returns vector of inverted-index timestamp-index"
       (let [[inverted-index timestamp-index] (idx/create-index "test/resources/test.log")]
         (fact "inverted-index contains words from log"
               (get-in inverted-index [:words "nesto"]) => vector?
               (get-in inverted-index [:words "zeljko"]) => vector?)
         (fact "word zeljko appears in 2 lines"
               (count (get-in inverted-index [:words "zeljko"])) => 2)
         (fact "timestamp-index has 10 entries (one per line)"
               (count timestamp-index) => 10)))

(def timestamp-index {0   (util/to-unix-timestamp "2026-01-19T10:00:00")
                      100 (util/to-unix-timestamp "2026-01-19T10:01:00")
                      200 (util/to-unix-timestamp "2026-01-19T10:02:00")
                      300 (util/to-unix-timestamp "2026-01-19T10:03:00")
                      400 (util/to-unix-timestamp "2026-01-19T10:04:00")})

(facts "get-timestamp-offsets, returns all timestamp offsets within time range [from, to]."
       (fact "from = nil, to = nil => return all"
             (idx/get-timestamp-offsets timestamp-index nil nil)
             => (contains [0 100 200 300 400]))
       (fact "from = date, to = nil => return only >= from"
             (idx/get-timestamp-offsets timestamp-index (util/to-unix-timestamp "2026-01-19T10:01:01") nil)
             => (contains [200 300 400]))
       (fact "from = nil, to = date => return only <= to"
             (idx/get-timestamp-offsets timestamp-index nil (util/to-unix-timestamp "2026-01-19T10:03:00"))
             => (contains [0 100 200 300]))
       (fact "from = date, to = date => return => from && <= to"
             (idx/get-timestamp-offsets timestamp-index (util/to-unix-timestamp "2026-01-19T10:01:01") (util/to-unix-timestamp "2026-01-19T10:05:01"))
             => (contains [200 300 400])))
