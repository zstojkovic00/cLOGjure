(ns clogjure.core
  (:require [clojure.string :as str])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))


(def log-path "src/resource/file.log")
(defn read-logs [filepath]  (str/split-lines (slurp filepath)))
(System/getProperty "user.dir")
(read-logs log-path)


(defn search-by-level [logs level]
  (reduce (fn [acc line]
            (if (str/includes? line level)
              (conj acc line)
              acc))
          []
          logs))


(def logs (read-logs log-path))

(search-by-level logs "WARN")
(search-by-level logs "INFO")


(LocalDateTime/now)

(defn write-log [filepath level msg]
  (let [timestamp (.format (LocalDateTime/now)
                           (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
        line (str timestamp " " level " " msg "\n")]
    (spit filepath line :append true)))


(write-log log-path "ERROR" "Nesto")
(def logs (read-logs log-path))
(search-by-level logs "ERROR")
