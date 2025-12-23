(ns first-project.core
  (:require [uncomplicate.commons.core :refer [with-release]]
            [uncomplicate.fluokitten.core :refer [fmap!]]
            [uncomplicate.neanderthal
             [native :refer [dv dge]]
             [core :refer [mv! mv axpy! scal!]]
             [math :refer [signum exp]]
             [vect-math :refer [fmax! tanh! linear-frac!]]]))

;; ============================================================================
;; NEURAL NETWORKS FROM SCRATCH
;; Michael Nielsen - Neural Networks and Deep Learning
;; ============================================================================

;; PART 1
(with-release [x (dv 0.3 0.9)
               w1 (dge 4 2 [0.3 0.6
                            0.1 2.0
                            0.9 3.7
                            0.0 1.0]
                       {:layout :row})
               h1 (dv 4)]
              (println (mv! w1 x h1)))

(with-release [x (dv 0.3 0.9)
               w1 (dge 4 2 [0.3 0.6
                            0.1 2.0
                            0.9 3.7
                            0.0 1.0]
                       {:layout :row})
               h1 (dv 4)
               w2 (dge 1 4 [0.75 0.15 0.22 0.33])
               y (dv 1)]
              (println (mv! w2 (mv! w1 x h1) y)))


;; PART 2
(defn step! [threshold x]
  (fmap! signum (axpy! -1.0 threshold (fmax! threshold x x))))

(let [threshold (dv 1 2 3)
      x (dv 0 2 7)]
  (step! threshold x))

(def x (dv 0.3 0.9))
(def w1 (dge 4 2 [0.3 0.6
                  0.1 2.0
                  0.9 3.7
                  0.0 1.0]
             {:layout :row}))
(def threshold (dv 0.7 0.2 1.1 2))

(step! threshold (mv w1 x))

(def bias (dv 0.7 0.2 1.1 2))
(def zero (dv 4))

(step! zero (axpy! -1.0 bias (mv w1 x)))

(step! bias (mv w1 x))

(defn relu! [threshold x]
  (axpy! -1.0 threshold (fmax! threshold x x)))

(relu! bias (mv w1 x))

(tanh! (axpy! -1.0 bias (mv w1 x)))

(defn sigmoid! [x]
  (linear-frac! 0.5 (tanh! (scal! 0.5 x)) 0.5))

(sigmoid! (axpy! -1.0 bias (mv w1 x)))

(with-release [x (dv 0.3 0.9)
               w1 (dge 4 2 [0.3 0.6
                            0.1 2.0
                            0.9 3.7
                            0.0 1.0]
                       {:layout :row})
               bias1 (dv 0.7 0.2 1.1 2)
               h1 (dv 4)
               w2 (dge 1 4 [0.75 0.15 0.22 0.33])
               bias2 (dv 0.3)
               y (dv 1)]
              (tanh! (axpy! -1.0 bias1 (mv! w1 x h1)))
              (println (sigmoid! (axpy! -1.0 bias2 (mv! w2 h1 y)))))