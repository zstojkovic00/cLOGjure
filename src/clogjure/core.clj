(ns clogjure.core
  (:require [clogjure.index :as idx]
            [clogjure.search :as search]
            [clogjure.state :as state]
            [clogjure.util :as util]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli])
  (:gen-class)
  (:import (java.io File)))

(def search-cli-options
  [["-h" "--help"]
   ["-A" "--any" "match ANY word (OR logic)"]
   ["-a" "--all" "match ALL words (AND logic)"]
   ["-p" "--prefix" "search by word prefix"]
   ["-f" "--from FROM" "start time filter"
    :parse-fn util/to-unix-timestamp
    :validate [some? "Invalid date format."]]
   ["-t" "--to TO" "end time filter"
    :parse-fn util/to-unix-timestamp
    :validate [some? "Invalid date format."]]
   ["-P" "--page PAGE" "500 per page, default 0"
    :default 0
    :parse-fn #(Integer/parseInt %)]])

(defn create-index
  "Creates a new index from log file and sets it as current."
  [log-path]
  (cond
    (nil? log-path) (println "Usage: index <path-to-log-file>")
    (not (.exists ^File (io/file log-path))) (println (str "File not found: " log-path))
    :else (do
            (idx/load-or-create-index log-path)
            (reset! state/current-session-log-path log-path)
            (println (str "Index loaded: " log-path)))))

(defn list-indexes
  "Prints all available index names to console."
  []
  (let [indexes (idx/list-indexes)]
    (if (empty? indexes)
      (println "No indexes available.")
      (doseq [index indexes]
        (println (str "  " index))))))

(defn select-index
  "Selects and loads an existing index by name."
  [index-name]
  (if (nil? index-name)
    (println "Usage: use <index-name>")
    (let [indexes (idx/list-indexes)
          match (first (filter (fn [index] (str/starts-with? index index-name)) indexes))]
      (if match
        (let [log-path (idx/load-log-path match)]
          (idx/load-or-create-index log-path)
          (reset! state/current-session-log-path log-path)
          (println (str "Index loaded: " match)))
        (println (str "Index not found: " index-name))))))

(defn search
  [args]
  (let [{:keys [options arguments errors]}
        (clojure.tools.cli/parse-opts args search-cli-options)
        words arguments
        from (:from options)
        to (:to options)
        page (:page options)
        search-fn (cond
                    (:prefix options) search/by-prefix-words
                    (:any options) search/by-or-words
                    :else search/by-and-words)]
    (cond
      errors (doseq [err errors] (println err))
      (nil? @state/current-session-log-path) (println "No index loaded.")
      (empty? words) (println "Usage: search <words> [--any] [--prefix] [--from DATE] [--to DATE]")
      :else
      (let [matching-lines (time (search-fn words @state/current-session-log-path from to page))]
        (when (seq matching-lines)
          (let [sorted-matching-lines (sort-by :score > matching-lines)]
            (println (str "Found " (count sorted-matching-lines) " results:\n"))
            (doseq [{:keys [score line]} sorted-matching-lines]
              (println (format "[%.2f] %s" (double score) line)))))))))

(defn clear-screen
  "Clears the terminal screen."
  []
  (print "\033[H\033[2J") ;; ANSI espace code
  (flush))

(defn index-status
  "Prints current index information."
  []
  (if (nil? @state/current-session-log-path)
    (println "No index loaded.")
    (let [log-path @state/current-session-log-path
          index-name (idx/to-index-path log-path :inverted)]
      (println (str "Index:    " index-name))
      (println (str "Log path: " log-path)))))

(defn -main
  "Starts the interactive CLI, reads and executes commands"
  []
  (loop []
    (print "clogjure>")
    (flush)
    (let [input (read-line)
          [command & args] (str/split input #" ")]
      (case command
        "index" (create-index (first args))
        "ls" (list-indexes)
        "use" (select-index (first args))
        "status" (index-status)
        "search" (search args)
        "clear" (clear-screen)
        "exit" :exit
        (println "Command does not exist"))
      (when-not (= command "exit")
        (recur)))))