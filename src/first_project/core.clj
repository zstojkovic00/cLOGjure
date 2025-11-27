(ns first-project.core)

(first [1 2 3 4])

(do (println "no prompt here!")
    (+ 1 3))
(=  true)


(= (list  4 5 6)) '(:a :b :c)

(=  (list 1 2 3 4) (conj '(3 4) 2 1))


(=  [:a :b :c] (list :a :b :c) (vec '(:a :b :c)) (vector :a :b :c))

(=  (set '(:a :a :b :c :c :c :c :d :d)))

(= #{:a :b :c :d} (clojure.set/union #{:a :b :c} #{:b :c :d}))

(= #{1 2 3 4} (conj #{1 4 3} 2))

(= __ ((hash-map :a 10, :b 20, :c 30) :b))

(hash-map :a 10 :b 20 :c 30)
((hash-map :a 10, :b 20, :c 30) :b)

(= {:a 1, :b 2, :c 3} (conj {:a 1} __ [:c 3]))

(rest [10 20 30 40])

((fn add-five [x] (+ x 5)) 3)

(= (* 2 2) 4)

(= (concat "Hello, " "Dave", "!") "Hello, Dave!")

(concat "Hello, " "Dave", "!")

(= (str "Hello, " "Dave" \!) "Hello, Dave!")

(str "Hello", \!)

(str "abc" \a)

(= (str "Hello, " "Rhea") "Hello, Rhea!")
(println (fn [x] ("Hello" + x + "!")))

(def add
  (fn [x y]
    (+ x y)))

(add 10 15)


(def myConcat(fn [x] (str "Hello, " x "!")))

(myConcat "Zeljko")

(def x1 1)

(+ x1)


