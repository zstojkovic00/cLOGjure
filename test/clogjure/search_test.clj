(ns clogjure.search-test
  (:require [clogjure.util :as util]
            [midje.sweet :refer :all]))

(def index {})

(facts "about offsets-intersection"
       (fact "returns intersection of byte offsets for multiple keywords"))