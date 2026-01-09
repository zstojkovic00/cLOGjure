(ns clogjure.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clogjure.index :as idx]))

(def current-index (atom nil))

;; TODO: add validation if args > 1
(defn create-index
  "Creates a new index from a log file path and sets it as current."
  [log-path]
  (if (nil? log-path)
    (println "Usage: index <path-to-log-file>")
    (if (not (.exists (io/file log-path)))
      (println (str "File not found: " log-path))
      (let [loaded (idx/get-or-create-index log-path)]
        (reset! current-index loaded)
        (println (str "Index loaded: " log-path))))))

(defn list-indexes
  "Lists all available indexes."
  []
  (let [indexes (idx/list-indexes)]
    (if (empty? indexes)
      (println "No indexes available.")
      (doseq [index indexes]
        (println (str "  " index))))))

;; TODO: add validation if args > 1
;; TODO: add validation if index is same as current one
(defn select-index
  "Selects and loads an existing index by name."
  [index-name]
  (if (nil? index-name)
    (println "Usage: use <index-name>")
    (let [indexes (idx/list-indexes)
          match (first (filter (fn [index] (str/starts-with? index index-name)) indexes))]
      (if match
        (let [loaded (idx/load-index (str idx/index-path match))]
          (reset! current-index loaded)
          (println (str "Loaded: " match)))
        (println (str "Index not found: " index-name))))))

(defn search
  "Searches the current index for a keywords"
  [keywords]
  (if (nil? @current-index)
    (println "No index loaded. Use index or use command first.")
    (if (nil? keywords)
      (println "Usage: search <keywords>")
      (println current-index))))

(defn clear-screen
  "Clears the terminal screen."
  []
  (print "\033[H\033[2J")
  (flush))


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
        "search" (search args)
        "clear" (clear-screen)
        "exit" :exit
        (println "Command does not exist"))
      (when-not (= command "exit")
        (recur))
      )
    )
  )