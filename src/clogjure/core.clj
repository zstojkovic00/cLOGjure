(ns clogjure.core
  (:require [clogjure.index :as idx]
            [clogjure.search :as search]
            [clogjure.state :as state]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn create-index
  "Creates a new index from log file and sets it as current."
  [log-path]
  (cond
    (nil? log-path) (println "Usage: index <path-to-log-file>")
    (not (.exists (io/file log-path))) (println (str "File not found: " log-path))
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
  "Searches the current index for words.
   Prints results ranked by relevance."
  [words]
  (if (nil? @state/current-session-log-path)
    (println "No index loaded. Use index or use command first.")
    (if (empty? words)
      (println "Usage: search <word1 word2 ...>")
      (let [results (search/by-exact-words words @state/current-session-log-path nil nil)]
        (when (seq results)
          (let [sorted (sort-by :score > results)]
            (println (str "Found " (count sorted) " results:\n"))
            (doseq [{:keys [score line]} sorted]
              (println (format "[%.2f] %s" (double score) line)))))))))

(defn clear-screen
  "Clears the terminal screen."
  []
  (print "\033[H\033[2J")
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