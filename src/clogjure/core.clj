(ns clogjure.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clogjure.index :as idx]
            [clogjure.search :as search]
            [clogjure.state :as state]))

;; TODO: add validation if args > 1
(defn create-index
  "Creates a new index from a log file path and sets it as current."
  [log-path]
  (if (nil? log-path)
    (println "Usage: index <path-to-log-file>")
    (if (not (.exists (io/file log-path)))
      (println (str "File not found: " log-path))
      (let [[inverted-index timestamp-index] (idx/get-or-create-index log-path)]
        (reset! state/current-index inverted-index)
        (reset! state/current-index-total-lines (count timestamp-index))
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
        (let [[inverted-index timestamp-index] (idx/load-index match)]
          (reset! state/current-index inverted-index)
          (reset! state/current-index-total-lines (count timestamp-index))
          (println (str "Index loaded: " match)))
        (println (str "Index not found: " index-name))))))

(defn search
  "Searches the current index for a keywords"
  [keywords]
  (if (nil? @state/current-index)
    (println "No index loaded. Use index or use command first.")
    (if (nil? keywords)
      (println "Usage: search <keywords>")
      (search/by-keyword keywords))))

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


;;; REPL Testing
(let [ [inverted-index timestamp_index] (idx/create-index "resources/logs/file.log")]
  (idx/persist-index-async "resources/logs/file.log" inverted-index timestamp_index)
  )
;
(idx/list-indexes)
(idx/get-or-create-index "resources/logs/file.log")
(select-index "file-inverted.idx")
(deref state/current-index)
(deref state/current-index-total-lines)
(reset! state/current-index nil)