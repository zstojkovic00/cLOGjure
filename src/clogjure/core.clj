(ns clogjure.core
  (:require [clojure.string :as str]
            [clogjure.index :as idx]))

(defn -main
  "Starts the interactive CLI, reads and executes commands"
  []
  (loop []
    (print "clogjure>")
    (flush)
    (let [input (read-line)
          [command & rest] (str/split input #" ")]
      (println (str/trim command))
      (println (map str/trim rest))
      (case command
        "index"~>
        "search"
        "clear" (do (print "\033[H\033[2J") (flush))
        "exit" :exit
        (println "Command does not exist")
        )
      (when-not (= command "exit")
        (recur))
      )
    ))

;;; REPL
(defn -main-repl [input]
  "Helper function to test CLI functionality in the REPL environment,
  takes an input string, and executes it once without starting the interactive loop"
  (let [[command & rest] (str/split input #" ")]
    (println (str/trim command))
    (println (map str/trim rest))
    (case command
      "index" (idx/get-or-create-index (first rest))
      "search"
      (println "Command does not exist"))))

(System/getProperty "user.dir")
(-main-repl "index resources/logs/file.log")

(idx/to-index-path "resources/logs/file.log" :inverted)
(idx/get-or-create-index "resources/logs/file.log")
(idx/load-index "resources/indexes/file-inverted.idx")