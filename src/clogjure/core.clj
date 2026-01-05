(ns clogjure.core
  (:require [clojure.string :as str]))

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
        "index"
        "search"
        "clear" (do (print "\033[H\033[2J") (flush))
        "exit" :exit
        (println "Command does not exist")
        )
      (when-not (= command "exit")
        (recur))
      )
    ))

(defn -main-repl [input]
  "Helper function to test CLI functionality in the REPL environment,
  takes an input string, and executes it once without starting the interactive loop"
  (let [[command & rest] (str/split input #" ")]
    (println (str/trim command))
    (println (map str/trim rest))
    (case command
      "index"
      "search"
      (println "Command does not exist"))))

(-main-repl "index log/file.log")
