(ns clogjure.util
  (:require [clojure.string :as str])
  (:import (java.time LocalDateTime ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(def formatters-path "resources/config/timestamp-formats.cfg")

(defn load-dt-formatters
  "Loads timestamp patterns from config file.
   Returns vector of {:regex :formatter} maps."
  []
  (let [formatters (slurp formatters-path)
        lines (str/split-lines formatters)]
    (mapv (fn [line]
            (let [[regex formatter] (str/split line #"\|")]
              {:regex (re-pattern (str "^" regex))
               :formatter (DateTimeFormatter/ofPattern formatter)}))
          lines)))

(def dt-formatters (load-dt-formatters))

(defn try-parse-timestamp
  "Tries to parse timestamp string with given formatter.
  Returns unix time in milliseconds or nil."
  [timestamp formatter]
  (try
    (let [^LocalDateTime local-dt (LocalDateTime/parse timestamp formatter)
          ^ZonedDateTime zoned-dt (.atZone local-dt (ZoneId/systemDefault))]
      (.toEpochMilli (.toInstant zoned-dt)))
    (catch Exception _ nil)))

(defn to-unix-timestamp
  "Tries to parse timestamp string with given formatters from config file until one matches.
  Returns unix time in milliseconds or nil."
  [line]
  (some (fn [{:keys [regex formatter]}]
          (when-let [match (re-find regex line)]
            (try-parse-timestamp match formatter)))
        dt-formatters))