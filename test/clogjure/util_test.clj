(ns clogjure.util-test
  (:require [clogjure.util :as util]
            [midje.sweet :refer :all]))

(facts "about to-unix-timestamp"
       (fact "parses ISO8601 with timezone offset (Spring Boot format)"
             (util/to-unix-timestamp "2025-12-05T22:17:01.524+01:00  INFO 7605 --- [abstractive-version]")
             => integer?)

       (fact "parses European format dd.MM.yyyy HH:mm:ss.SSS"
             (util/to-unix-timestamp "14.01.2026 00:00:00.005 [scheduling-1] INFO  r.y.b.w.s.AsyncRequestSchedulerService")
             => integer?)

       (fact "parses Log4j format yyyy-MM-dd HH:mm:ss,SSS"
             (util/to-unix-timestamp "2026-01-14 00:00:00,034 INFO  [rs.firma.billdev.bmsws.util.WsInterceptor]")
             => integer?)

       (fact "returns nil for lines without timestamp"
             (util/to-unix-timestamp "SLF4J: Class path contains multiple SLF4J bindings.")
             => nil)

       (fact "returns nil for empty string"
             (util/to-unix-timestamp "")
             => nil))
