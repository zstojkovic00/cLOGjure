(ns clogjure.search
  (:require [clogjure.state :as state]))

(defn by-keywords [keywords]
  (let [index @state/current-index]
    index))
