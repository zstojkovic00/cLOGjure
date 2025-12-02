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


(str "zeljko" " je kralj")

(if true "Zeljko" "Nesto")
(if false "Zeljko" "Nesto")



(if true (do (println "Success!") "Zeljko")
         (do (println) "Failure!" "Nezeljko")
         )

(when true (println "Success!") "abra cadabra")


(nil? 1)
(nil? nil)
(if "bears eat bears" "nesto")


; Operator OR vraca prvu true vrednost ili zadnju vrednost
; Operator AND vraca prvu false vrednost ili ako nema false vrednosti poslednju true


; OR
(or false nil :large_I_mean_venti)
(or true nil :large_I_mean_venti)
(or nil true :large_I_mean_venti)

(or (= 0 1) (= "yes" "no"))

(or nil)


; AND

(and :free_wifi :hot_coffee)

(and :feelin_super_cool nil false)
(and false nil :feelin_super_cool)

; def se koristi da se binduje ime ka vrednosti u clojure

(def failed-protagonist-names ["Larry Potter" "Doreen the Explorer" "The Incredible Bulk"])

(identity failed-protagonist-names)


(def severity :mild)
(def error-message "OH GOD! IT'S A DISASTER! WE'RE ")


; Ovo je pogresan nacin da se pise Clojure
(if (= severity :mild)
  (def error-message (str error-message "MILDY INCONVENIENCED!"))
  (def error-message (str error-message "DOOOOOOMED!")))

(identity error-message)


; Ovako bi trebalo

; definisali smo funkciju error-message koja prihvata jedan argument,
; onda radimo concat stringova i proveravamo da li je severity jednako mild ako jeste dodajemo prvi ako ne drugi string.
(defn error-message
  [severity]
  (str "OH GOD! IT'S A DISASTER! WE'RE "
       (if (= severity :mild)
         "MILDLY INCONVENIENCED!"
         "DOOOOOOOMED!")))


(error-message :zeljko)
(error-message :mild)


; Sve strukture podataka u clojure su imutabilne, ne mogu da se menjaju

; Samo "" su validni stringovi '' nije valdan string

; Maps

{}

(def person {:first-name "Zeljko"
             :last-name "Stojkovic"})

(or person (throw NullPointerException))


(identity person)

(get person :first-name)
(:first-name person)
(person :first-name)

(hash-map :a 1 :b 2)


(get {:a 0 :b 1} :a)
(get {:a 0 :b 1 :c 2} :c "unicorns?")

(get [3 2 1] 0)

; Razlika izmedju vektora i liste, vektor sintaksa [1 2 3 4], lista (1 2 3 4)
; java poredjenje list je LinkedList a vector je ArrayList
; ako zelimo da dodajemo iteme mnogo linkedlist je dosta bolji jer ne moramo da pomeramo elemente mnogo,
; ako zelimo samo da pristupamo nekim elementima onda zelimo da korsitimo vektore


; sa apostrofom clojure tretira kao skup podataka a ne kao funkciju
'( 1 2 3 4)
(nth '(:a :b :c) 0)

(conj '(1 2 3) 4)

(last [1 2 3 4])

(or + -)

((or + -) 1 2 3)
; +1
(inc 1.1)

(map inc [0 1 2 3])

; Definicija funkcije se sastoji iz 5 delova:
; defn
; Ime funkcije
; docstring opciono
; Parametri u []
; Telo funkcije

(defn funkcija
  "Ova funkcija nesto radi"
  [name]
  (str "Kralj je " name ))

(funkcija "Zeljko")

(doc funkcija)


; funckija moze da bude sa vise razlicitih ulaza nesto kao u javi overloading sem sto ne moze sa jednim parametrom posto nije staticki jezik

(defn x-chop
  "Describe the kind of chop you're inflicting on someone"
  ([name chop-type]
   (str "I " chop-type " chop " name "! Take that!"))
  ([name]
   (x-chop name "karate")))

(x-chop "Kanye West" "slap")
(x-chop "Kanye East")

; Destructuring
(defn my-first
  [[first-thing]] ; Notice that first-thing is within a vector
  first-thing)

(my-first ["oven", "bike" "war-axe"])


; private String numberComment(int x) {
;     if(x > 6) return "What a big number
;      return That number's OK
; }
(defn number-comment
  [x]
  (if (> x 6)
    "Oh my gosh! What a big number!"
    "That number's OK, I guess"))

(number-comment 5)
(number-comment 7)


; Anonimne funkcije su one koje nemaju ime

(map (fn [name] (str "Hi, " name))
     ["Darth Vader" "Mr. Magoo"])


(def asym-hobbit-body-parts [{:name "head" :size 3}
                             {:name "left-eye" :size 1}
                             {:name "left-ear" :size 1}
                             {:name "mouth" :size 1}
                             {:name "nose" :size 1}
                             {:name "neck" :size 2}
                             {:name "left-shoulder" :size 3}
                             {:name "left-upper-arm" :size 3}
                             {:name "chest" :size 10}
                             {:name "back" :size 10}
                             {:name "left-forearm" :size 3}
                             {:name "abdomen" :size 6}
                             {:name "left-kidney" :size 1}
                             {:name "left-hand" :size 2}
                             {:name "left-knee" :size 2}
                             {:name "left-thigh" :size 4}
                             {:name "left-lower-leg" :size 3}
                             {:name "left-achilles" :size 1}
                             {:name "left-foot" :size 2}])


(defn matching-part
  [part]
  {:name (clojure.string/replace (:name part) #"^left-" "right-")
   :size (:size part)})

(defn symmetrize-body-parts
  "Expects a seq of maps that have a :name and :size"
  [asym-body-parts]
  (loop [remaining-asym-parts asym-body-parts
         final-body-parts []]
    (if (empty? remaining-asym-parts)
      final-body-parts
      (let [[part & remaining] remaining-asym-parts]
        (recur remaining
               (into final-body-parts
                     (set [part (matching-part part)])))))))


(symmetrize-body-parts asym-hobbit-body-parts)

(let [x 3] x)

(def dalmatian-list ["Pongo" "nesto" "nesto2"])

(let [dalmatians (take 2 dalmatian-list)] dalmatians)

(def x 0)
(let [x 1] x)                                               ; Menja x ali samo u kontekstu te funckije u globalnom kontekstu i dalje je 0
(identity x)


; & dalmatians2
(let [ [pongo ponogo2 pongo23 & dalmatians]  dalmatian-list]
  [pongo ponogo2 pongo23 dalmatians])

(into [] (set [:a :a :b]))

(loop [iteration 0]
  (println (str "Iteration " iteration))
  (if (> iteration 3)
    (println "Goodbye!")
    (recur (inc iteration))))

; recur poziva ponovo najblizu loop ili funkciju sa novim argumentima
; clojure loop je rekurzija -.-

(doseq [x [1 2 3 4 5]]
  (println x)
  )

(doseq [i (range 5)]
  (println i))

(map println [1 2 3 4 5])
(map inc [1 2 3 4 5])
(filter even? [1 2 3 4 5 6])
(reduce + [1 2 3 4 5])


(defn add100 [x] (+ 100 x))

(add100 100)

(defn mapset [coll] (reduce conj #{} coll))

(defn mapset [coll]
  (set coll))
(mapset [1 1 2 2])

(clojure.string/split "zeljko je kralj", #" ")

(= (nth '(4 5 6 7) 2) 6)


(nth [1 2 3] 0) ()



(myNth [1 2 3] 0)


(get [1 2 3] 1)



(nth '(4 5 6 7) 2)

(map inc [1 2 3 4 5])

; Sta treba da napisem? Treba da prodjem kroz celu listu i
; da vratim objekat koji je na mestu i, znaci treba listu da pomerim na i poziciju

(map count ["zeljko", "nesto", "nesto123"])

(def person [{:name "Pera" :age 30} {:name "Zeljko" :age 25}])

(reduce map :age person)
(count person )

(/ (reduce + (map :age person)) (count person))

;; domaci slican primen za svoj domen, 3-4 primera


;; (reduce (fn [acc e]  [(inc (acc 0)) (+ (acc 1) e)]))


(apply / (reduce (fn [[cnt sum] e] [(+ sum e) (inc cnt)])
                 [0 0]
                 [1 2 3]))
;; 6 / 3 = 2
; [ (inc (acc 0) (+ (acc 1) e)])
(reduce + (map :age person))


;; uzima akumulator i element
;; akumulator je 0 prvi element je 30
;; posto radi reduce on vraca 0 i 30 => 30
;; prelazi na drugi element akumulator je sada 30 a drugi element je 25
;; sada je akumulator 55 a nema vise elemenata i to je vraceno
;; akumulator mu ne treba, moze i bez akumulatora
(/ 6 3)
