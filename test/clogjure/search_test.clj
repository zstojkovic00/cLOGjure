(ns clogjure.search-test
  (:require [midje.sweet :refer :all]
            [clogjure.search :as search]
            [clogjure.util :as util]))

(fact "Inverse Document Frequency, calculates how rare word is across log"
      (fact "word appears five times in log of 100 lines"
            (search/idf 100 5) => (roughly 2.9 0.1))
      (fact "word does not appear - division by zero"
            (search/idf 100 0) => (throws ArithmeticException)))

(fact "Term Frequency - Inverse Document Frequency"
      (fact "basic multiplication"
            (search/tf-idf 5 10) => 50))

(facts "intersection"
       (fact "returns common offsets present in all vectors"
             (search/intersection [[0 100 200] [100 100 300]]) => [100] :in-any-order
             (search/intersection [[0 0 200] [200 100 500] [200]]) => [200] :in-any-order)
       (fact "returns offsets present in one vector"
             (search/intersection [[0 100 200]]) => [0 100 200] :in-any-order)
       (fact "returns empty vector for empty input"
             (search/intersection []) => []))

(facts "union"
       (fact "returns unique offsets for input vectors"
             (search/union [[0 100] [100 200]]) => [0 100 200] :in-any-order
             (search/union [[0 100 100] [100 200 300]]) => [0 100 200 300] :in-any-order)
       (fact "returns offsets present in one vector"
             (search/union [[0 100 200]]) => [0 100 200] :in-any-order)
       (fact "returns empty vector for empty input"
             (search/union []) => []))

(def test-log-path "test/resources/test.log")

(facts "by-and-words - searches log with AND logic"
       (fact "single word search returns matching lines"
             (let [matching-lines (search/by-and-words ["zeljko"] test-log-path nil nil 0)]
               matching-lines => vector?
               (count matching-lines) => 2                  ;; word zeljko appears in two lines [8, 10]
               (get-in matching-lines [0 :line]) => "2026-01-19T10:02:00 INFO zeljko je kralj"
               (get-in matching-lines [1 :line]) => "2026-01-19T10:00:00 INFO zeljko"))

       (fact "multiple words search returns matching lines"
             (let [matching-lines (search/by-and-words ["AbstractiveVersionControlSystemManagerApplicationKt" "process"] test-log-path nil nil 0)]
               matching-lines => vector?
               (count matching-lines) => 1                  ;; only one line [6] contains both AbstractiveVersionControlSystemManagerApplicationKt and process
               (get-in matching-lines [0 :line]) => (contains "Started AbstractiveVersionControlSystemManagerApplicationKt in 1.034 seconds (process running for 1.227)")))

       (fact "multiple words search with date filter"
             (let [matching-lines (search/by-and-words ["AbstractiveVersionControlSystemManagerApplicationKt" "process"] test-log-path
                                                       (util/to-unix-timestamp "2025-12-05T22:15:00.338+01:00") (util/to-unix-timestamp "2025-12-05T22:20:01.338+01:00") 0)]
               matching-lines => vector?
               (count matching-lines) => 1
               (get-in matching-lines [0 :line]) => #(.startsWith % "2025-12-05T22:17:02.338+01:00  INFO 7605")))

       (fact "multiple words search with date filter out of range"
             (let [matching-lines (search/by-and-words ["AbstractiveVersionControlSystemManagerApplicationKt" "process"] test-log-path
                                                       (util/to-unix-timestamp "2025-12-05T22:30:00.338+01:00") (util/to-unix-timestamp "2025-12-05T22:35:01.338+01:00") 0)]
               matching-lines => nil
               (count matching-lines) => 0
               (get-in matching-lines [0 :line]) => nil)))

(facts "by-prefix - searches log by prefix"
       (fact "single word search returns matching lines"
             (let [matching-lines (search/by-prefix-words ["zelj"] test-log-path nil nil 0)]
               matching-lines => vector?
               (count matching-lines) => 2                  ;; prefix zelj appears in two lines [8, 10]
               (get-in matching-lines [0 :line]) => "2026-01-19T10:02:00 INFO zeljko je kralj"
               (get-in matching-lines [1 :line]) => "2026-01-19T10:00:00 INFO zeljko")

             (let [matching-lines (search/by-prefix-words ["rad"] test-log-path nil nil 0)]
               matching-lines => vector?
               (count matching-lines) => 1                  ;; prefix rad appears in one line
               (get-in matching-lines [0 :line]) => "2026-01-19T10:01:00 ERROR nesto ne radi...")

             (let [matching-lines (search/by-prefix-words ["info"] test-log-path nil nil 0)]
               matching-lines => vector?
               (count matching-lines) => 8))                ;; prefix info appears in eight lines

       (fact "non-existent prefix returns nil"
             (search/by-prefix-words ["asd"] test-log-path nil nil 0) => nil))

(facts "by-or-words - searches log with OR logic"
       (fact "single word search returns matching lines"
             (let [matching-lines (search/by-or-words ["zeljko"] test-log-path nil nil 0)]
               matching-lines => vector?
               (count matching-lines) => 2))

       (fact "multiple words OR returns lines containing any word"
             (let [matching-lines (search/by-or-words ["zeljko" "error"] test-log-path nil nil 0)]
               matching-lines => vector?
               (count matching-lines) => 4))                ;; word zeljko appears in 2 lines and word error appears in 2 lines as well

       (fact "non-existent words returns nil"
             (search/by-or-words ["asd" "dsa"] test-log-path nil nil 0) => nil))
