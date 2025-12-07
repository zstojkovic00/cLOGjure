(ns clogjure.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))


;; Naive implementation
(def log-path "resources/file.log")
;;(def big-log-path "resources/spark300MB.log")
(defn read-logs [filepath] (str/split-lines (slurp filepath)))
(System/getProperty "user.dir")
(read-logs log-path)
;;(read-logs big-log-path)


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
                           (DateTimeFormatter/ofPattern "yyyy-MM-ddTHH:mm:ss[.mmm]"))
        line (str timestamp " " level " " msg "\n")]
    (spit filepath line :append true)))

(write-log log-path "ERROR" "Nesto")
(def logs (read-logs log-path))
(search-by-level logs "ERROR")

;; Buffer reader - streaming
(def index-path "resources/file-index.log")

(defn read-logs-streaming [filepath indexpath level]
  (with-open [rdr (io/reader filepath)
              w (io/writer indexpath)]
    (doseq [ [index line] (map-indexed vector (line-seq rdr))]
      (if (str/includes? line level)
        (.write w (str "line in orginal file: " index "\n" line "\n"))))))

(read-logs-streaming log-path index-path "ERROR")

(doseq [x [1 2 3]
        y [1 2 3]]
  (prn (* x y)))

(doseq [[i x] (map-indexed vector [1 2 3])]
  (println i ":" x))

;; TODO: Implement line byte size
