# UNIVERZITET U BEOGRADU FAKULTET ORGANIZACIONIH NAUKA

## PROJEKTNA DOKUMENTACIJA IZ PREDMETA ALATI I METODE SOFTVERSKOG INZENJERSTVA

**TEMA:** cLOGjure - Pretrazivac aplikativnih log fajlova

**Mentor:** dr Dragan Djuric

**Student:** Zeljko Stojkovic 2025/3806

Beograd, 2026. godine

---

## Sadrzaj

1. [Uvod](#1-uvod)
2. [Arhitektura sistema](#2-arhitektura-sistema)
   - 2.1. [Invertovani indeks](#21-invertovani-indeks)
   - 2.2. [Vremenski indeks](#22-vremenski-indeks)
   - 2.3. [Pretraga log fajla](#23-pretraga-log-fajla)
   - 2.4. [Tok sesije i upravljanje stanjem](#24-tok-sesije-i-upravljanje-stanjem)
3. [Opis koriscenih tehnologija](#3-opis-koriscenih-tehnologija)
4. [Korisnicko uputstvo](#4-korisnicko-uputstvo)
5. [Prilog](#5-prilog)

---

## 1. Uvod

CLOGjure je CLI interaktivna aplikacija za pretragu aplikativnih log fajlova, ciji je razvoj inicijalno bio inspirisan ELK Stack [1] sistemom i njegovim pristupom radu sa indeksima. ELK Stack (Elasticsearch, Logstash, Kibana) predstavlja skup alata otvorenog koda koji se koriste za prikupljanje, obradu, pretragu i vizualizaciju log fajlova. Sastoji se od Elasticsearch-a, koji omogucava indeksiranje i pretragu podataka, Logstash-a, koji sluzi za prikupljanje i transformaciju logova, i Kibana-e, koja omogucava vizualizaciju i analizu podataka. U ovom seminarskom radu razmatra se iskljucivo tema indeksiranja i pretrage podataka.

Naknadnim istrazivanjem je utvrdjeno da Elasticsearch svoje kljucne funkcionalnosti pretrage i indeksiranja zasniva na Apache Lucene [2] biblioteci. Daljom analizom Apache Lucene biblioteke je primeceno da se osnovni koncepti koje ona primenjuje poklapaju sa onim sto je implementirano u cLOGjure aplikaciji. Ovi koncepti, poput invertovanog indeksa i rangiranja rezultata pomocu statistickih algoritama kao sto je TF-IDF, predstavljaju de-facto standard u izradi sistema za pretragu, tako da njihova primena u cLOGjure nije bila namerna, vec je proizasla iz prirodnog resavanja problema pretrage velikih log fajlova.

---

## 2. Arhitektura sistema

Pretrazivanje velikih log fajlova predstavlja problem iz perspektive racunarskih resursa, postavlja se pitanje kako efikasno procitati fajl sa diska i izvrsiti pretragu nad njim? Ukoliko log fajl ima velicinu od nekoliko gigabajta, njegovo kompletno ucitavanje u radnu memoriju svaki put kada radimo pretragu postaje neprakticno, takodje i sama pretraga nad takvim fajlom je spora.
Klasicna pretraga log fajla ima linearnu vremensku slozenost O(n), neophodno je proci kroz svaku liniju fajla i proveriti da li sadrzi trazenu rec. Alati poput grep-a [3] rade na ovaj nacin, ovo je prihvatljivo za jednokratnu pretragu nad manjim fajlovima, ali za ponovljene pretrage nad velikim log fajlovima nije efikasno. Problem postaje jos izrazeniji kod pretrage po prefiksu, gde se traze sve reci koje pocinju odredjenim nizom karaktera. U tom slucaju, za svaku liniju je potrebno proveriti svaku rec, sto dodatno uvecava broj operacija.

Resenje ovog problema zahteva drugaciji pristup, umesto konstantnog ocitavanja fajla sa diska pri svakom upitu, potrebno je jednom obraditi njegov sadrzaj i kreirati dodatnu strukturu podataka koja omogucava brz pristup trazenim informacijama.
Ovakav pristup uvodi kompromis izmedju vremena i prostora, jer zahteva dodatno vreme pri inicijalizaciji i memoriju za izgradnju indeksa, ali zauzvrat omogucava znatno brzu pretragu prilikom svakog narednog upita, ovaj trade-off predstavlja osnovu arhitekture cLOGjure sistema.

### 2.1. Invertovani indeks

Predstavlja strukturu podataka koja mapira reci na njihove lokacije u okviru skupa dokumenata ili tekstualnih zapisa. Invertovani indeks u kontekstu cLOGjure aplikacije predstavlja pojednostavljenu i prilagodjenu verziju ovog koncepta s obzirom da indeksira iskljucivo jedan log fajl, pri cemu se reci mapiraju na pozicije bajtova linija na kojima se te reci nalaze.

Primer, imamo log fajl sa 3 linije, gde je n pozicija bajtova linije:

```
Line 0, byte n1: 2026-01-19T10:00:00 INFO server je startovan\n
Line 1, byte n2: 2026-01-19T10:01:00 ERROR desila se neka greska\n
Line 2, byte n3: 2026-01-19T10:02:00 INFO server radi nesto\n
```

Svaka linija se parsira tako sto se izdvaja vremenska oznaka (timestamp), koja se zatim konvertuje u Unix timestamp format radi standardizacije. Preostali tekstualni sadrzaj se normalizuje pretvaranjem u mala slova i deli na pojedinacne reci.

```
Line 0, byte n1: timestamp: 1737277200000 tokeni: [server, je, startovan]
```

Nakon parsiranja, formira se invertovani indeks u kome se svaka rec mapira na listu pozicija bajtova na kojima se ta rec pojavljuje.

```
server   → [n1, n3]
je       → [n1]
startovan→ [n1]
```

Isti postupak se primenjuje za sve reci iz log fajla.

### Implementacija invertovanog indeksa

Funkcija `tokenize` normalizuje tekst pretvaranjem u mala slova, uklanja sve ne-alfanumericke karaktere i deli string po razmacima:

```clojure
(defn tokenize
  [line]
  (let [clean (str/replace (str/lower-case line) #"[^a-z0-9]" " ")]
    (filter not-empty (str/split clean #" +"))))
```

Funkcija `split-line-by-timestamp` pokusava da prepozna timestamp na pocetku linije koristeci listu formata iz konfiguracije (resources/config/timestamp-formats.cfg) i potom radi split po tom timestamp-u.

```clojure
(defn split-line-by-timestamp
  [line]
  (let [matched (some (fn [{:keys [regex formatter]}]
                        (let [match (re-find regex line)]
                          (when match
                            {:match match :formatter formatter})))
                      util/dt-formatters)]
    (if matched
      (let [unix-timestamp (util/try-parse-timestamp (:match matched) (:formatter matched))
            content (str/trim (subs line (count (:match matched))))]
        [unix-timestamp content])
      [nil line])))
```


Funkcija `create-index` parsira log fajl u jednom prolazu koristeci `reduce`. Za svaku liniju racuna poziciju bajtova, izdvaja timestamp, tokenizuje sadrzaj i azurira oba indeksa. Rezultat se na kraju konvertuje u `sorted-map` radi efikasne prefix pretrage:

```clojure
(defn create-index
  [log-path]
  (with-open [rdr (io/reader log-path)]
    (let [indexes
          (reduce
           (fn [[outer-inverted-acc timestamp-acc current-offset] line]
             (let [[unix-timestamp content] (split-line-by-timestamp line)
                   words (tokenize content)
                   line-length (count (.getBytes line))
                   new-offset (+ current-offset line-length 1)

                   updated-timestamp-index (if unix-timestamp
                                             (assoc timestamp-acc current-offset unix-timestamp)
                                             timestamp-acc)

                   updated-inverted-index (reduce
                                           (fn [inner-inverted-acc word]
                                             (update-in inner-inverted-acc [:words word]
                                                        (fnil conj []) current-offset))
                                           outer-inverted-acc
                                           words)]
               [updated-inverted-index updated-timestamp-index new-offset]))
           [{:words {}} {} 0]
           (line-seq rdr))]

      (let [[inverted-index timestamp-index _] indexes]
        [(assoc inverted-index :words (into (sorted-map) (:words inverted-index)))
         timestamp-index]))))
```

### 2.2. Vremenski indeks

Pored invertovanog indeksa, cLOGjure aplikacija koristi i vremenski indeks kako bi se omogucila efikasna pretraga log zapisa po vremenskom opsegu. Za svaku liniju cuva se njen Unix timestamp zajedno sa njenom pozicijom bajtova u fajlu.

Primer zapisa u vremenskom indeksu za prvu liniju:

```
n1 → 1737277200000
```

Funkcija `get-timestamp-offsets` filtrira pozicije bajtova koji pripadaju zadatom vremenskom opsegu `[from, to]`, sto omogucava pretragu logova u okviru specificnog vremenskog opsega

```clojure
(defn get-timestamp-offsets
  [timestamp-index from to]
  (vec (for [[offset ts] timestamp-index
             :when (and (or (nil? from) (>= ts from))
                        (or (nil? to) (<= ts to)))]
         offset)))
```

### 2.3. Pretraga log fajla

Nakon sto su formirani invertovani i vremenski indeksi, imamo sva neophodna sredstva za efikasno izvrsavanje pretrage nad log fajlovima, umesto sekvencijalnog citanja, pretraga se svodi na operacije nad indeksom, kao sto su presek i unija skupova pozicija bajtova.

cLOGjure omogucava pretragu reci na tri nacina:

**1. Pretraga reci (AND pretraga)** - sve reci iz upita moraju biti prisutne u istoj liniji log fajla. Za svaku rec iz upita pronalaze se odgovarajuce liste pozicija bajtova iz invertovanog indeksa, nakon cega se nad njima racuna presek.

**Presek skupova pozicija bajtova:**

```clojure
(defn intersection
  [offsets]
  (if (empty? offsets)
    []
    (let [offset-sets (map set offsets)]
      (vec (apply set/intersection offset-sets)))))
```

Funkcija `by-and-words` pronalazi pozicije bajtova za svaku rec, racuna presek (linije koje sadrze sve reci), primenjuje opcioni vremenski filter, i rangira rezultate po TF-IDF skoru:

```clojure
(defn by-and-words
  [words log-path from to]
  (let [[inverted-index timestamp-index] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to)
                           (idx/get-timestamp-offsets timestamp-index from to))
        word-offsets (map (fn [word]
                           (idx/get-inverted-offsets inverted-index word)) words)
        all-offsets (if timestamp-offsets
                     (cons timestamp-offsets word-offsets)
                     word-offsets)
        intersected-offsets (intersection all-offsets)]
    (if (empty? intersected-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            lines-with-words intersected-offsets
            lines (idx/load-index-lines lines-with-words log-path)
            idf-score (idf total-lines (count lines-with-words))]
        (mapv
          (fn [{:keys [offset line]}]
            (let [tf-score (tf intersected-offsets offset)]
              {:offset offset
               :score  (tf-idf tf-score idf-score)
               :line   line}))
          lines)))))
```

**2. Pretraga reci (OR pretraga)** - rezultat sadrzi linije koje imaju bilo koju od trazenih reci. Za svaku rec se pronalaze pozicije bajtova, zatim se racuna unija svih skupova.

**Unija skupova pozicija bajtova:**

```clojure
(defn union
  [offsets]
  (if (empty? offsets)
    []
    (let [offsets-set (map set offsets)]
      (vec (apply set/union offsets-set)))))
```

Funkcija `by-or-words` radi slicno AND pretrazi, ali umesto preseka koristi uniju pozicija bajtova, sto znaci da rezultat sadrzi linije koje imaju bar jednu od trazenih reci:
- Presek skupova (intersection) se koristi zbog vremenskog indeksa

```clojure
(defn by-or-words
  [words log-path from to]
  (let [[inverted-index timestamp-index] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to)
                           (idx/get-timestamp-offsets timestamp-index from to))
        word-offsets (map (fn [word]
                           (idx/get-inverted-offsets inverted-index word)) words)
        union-offsets (union word-offsets)
        intersected-offsets (intersection
                             (if timestamp-offsets
                               [union-offsets timestamp-offsets]
                               [union-offsets]))]
    (if (empty? intersected-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            lines-with-words intersected-offsets
            lines (idx/load-index-lines lines-with-words log-path)
            idf-score (idf total-lines (count lines-with-words))]
        (mapv
          (fn [{:keys [offset line]}]
            (let [tf-score (tf intersected-offsets offset)]
              {:offset offset
               :score  (tf-idf tf-score idf-score)
               :line   line}))
          lines)))))
```

**3. Pretraga po prefiksu** - traze se sve reci koje pocinju zadatim prefiksom. 

Funkcija `by-prefix-words` koristi `subseq` nad sortiranom mapom da pronadje sve reci koje pocinju datim prefiksom, zatim uzima uniju svih njihovih pozicija bajtova:
- Presek skupova (intersection) se koristi zbog vremenskog indeksa

```clojure
(defn by-prefix-words
  [prefixes log-path from to]
  (let [[inverted-index timestamp-index] (idx/load-or-create-index log-path)
        timestamp-offsets (when (or from to)
                           (idx/get-timestamp-offsets timestamp-index from to))
        matching-words (mapcat (fn [prefix]
                                 (take-while (fn [word] (.startsWith word prefix))
                                             (map key (subseq (:words inverted-index) >= prefix))))
                               prefixes)
        word-offsets (map (fn [word]
                           (idx/get-inverted-offsets inverted-index word)) matching-words)
        union-offsets (union word-offsets)
        intersected-offsets (intersection
                             (if timestamp-offsets
                               [union-offsets timestamp-offsets]
                               [union-offsets]))]
    (if (empty? intersected-offsets)
      (println "No results found.")
      (let [total-lines (count timestamp-index)
            lines-with-words intersected-offsets
            lines (idx/load-index-lines lines-with-words log-path)
            idf-score (idf total-lines (count lines-with-words))]
        (mapv
          (fn [{:keys [offset line]}]
            (let [tf-score (tf intersected-offsets offset)]
              {:offset offset
               :score  (tf-idf tf-score idf-score)
               :line   line}))
          lines)))))
```

**Citanje linija sa pozicije bajtova:**

Funkcija `load-index-lines` koristi `RandomAccessFile` za direktan pristup linijama bez citanja celog fajla. Seek operacija pozicionira citac na tacnu poziciju bajtova:

```clojure
(defn load-index-lines
  [offsets log-path]
  (with-open [raf (RandomAccessFile. ^String log-path "r")]
    (mapv
     (fn [offset]
       (.seek raf offset)
       {:offset offset
        :line   (.readLine raf)})
     offsets)))
```


**TF-IDF rangiranje rezultata**

Rezultati pretrage se rangiraju pomocu TF-IDF (Term Frequency - Inverse Document Frequency) statistickog algoritma. Ovaj algoritam odredjuje relevantnost svake linije tako sto kombinuje dva faktora:

- **TF (Term Frequency)** - koliko puta se trazena rec pojavljuje u konkretnoj liniji. Linija u kojoj se rec pojavljuje vise puta se smatra relevantnijom.

- **IDF (Inverse Document Frequency)** - koliko je rec retka u celom log fajlu. Reci koje se pojavljuju u manjem broju linija imaju veci IDF, sto znaci da su relevantnije. Formula: `log(N / df)` gde je N ukupan broj linija, a df broj linija u kojima se rec pojavljuje.

Konacni skor za svaku liniju je proizvod TF i IDF vrednosti. Rezultati se sortiraju po ovom skoru od najveceg ka najmanjem.


```clojure
(defn tf
  [offsets offset]
  (count (filter #(= % offset) offsets)))

(defn idf
  [total-lines lines-with-word]
  (Math/log (/ total-lines lines-with-word)))

(defn tf-idf
  [tf idf]
  (* tf idf))
```

### 2.4. Tok sesije i upravljanje stanjem

Prilikom koriscenja aplikacije postoje dva scenarija u zavisnosti od toga da li indeks za dati log fajl vec postoji na disku.

**Scenario 1 - prvi put (indeks ne postoji):**

1. Korisnik pokrece komandu `index logs/app.log`
2. Aplikacija parsira ceo log fajl i kreira invertovani i vremenski indeks u memoriji
3. Pretraga radi nad indeksom iz memorije
4. Indeks se asinhrono cuva na disk (za sledeci put)
5. Aplikacija se gasi - memorija nestaje, ali fajlovi na disku ostaju

**Scenario 2 - ponovljeno koriscenje (indeks postoji na disku):**

1. Korisnik pokrece komandu `use app`
2. Aplikacija detektuje da index fajl vec postoji na disku i ucitava ga u memoriju (brze od ponovnog parsiranja)
3. Pretraga radi nad indeksom iz memorije

Oba scenarija su objedinjena u jednoj memoizovanoj funkciji `load-or-create-index` koja implementira trostepeni pristup: 
1. Proverava memoriju (memoizacija), 
2. Proverava disk,
3. Ako ni jedno ni drugo ne postoji - kreira novi indeks:

```clojure
(def load-or-create-index
  (memoize
   (fn [log-path]
     (let [inverted-path (to-index-path log-path :inverted)]
       (if (.exists (io/file inverted-path))
         (load-index log-path)
         (let [[inverted-index timestamp-index] (create-index log-path)]
           (persist-index-async log-path inverted-index timestamp-index)
           [inverted-index timestamp-index]))))))
```

Memoizacija garantuje da se za isti `log-path` indeks ucitava samo jednom - svaki sledeci poziv vraca kesirani rezultat iz memorije bez ponovnog citanja sa diska.

Asinhrono cuvanje indeksa na disk koristi `future` za neblokirajuce pisanje. Invertovani indeks se cuva u formatu `rec offset1 offset2 ...`, a vremenski indeks kao `offset timestamp`:

```clojure
(defn persist-index-async
  [log-path inverted-index timestamp-index]
  (let [index-name (-> (to-index-path log-path :inverted) io/file .getName)]
    (future
      (with-open [w (io/writer (to-index-path log-path :timestamp))]
        (doseq [[offset timestamp] timestamp-index]
          (.write w (str offset " " timestamp "\n"))))

      (with-open [w (io/writer (to-index-path log-path :inverted))]
        (doseq [[word offsets] (:words inverted-index)]
          (.write w (str word " " (str/join " " offsets) "\n"))))

      (let [registry (list-registry)]
        (if (nil? (get registry index-name))
          (with-open [w (io/writer registry-path :append true)]
            (.write w (str index-name " " log-path "\n"))))))))
```

S obzirom da korisnik moze tokom jedne sesije ucitati vise razlicitih log fajlova, aplikacija koristi jedan atom za pracenje trenutno aktivnog loga:

```clojure
(def current-session-log-path (atom nil))
```

Memoizacija kesira indekse za sve ucitane log fajlove, ali ne zna koji je trenutni - atom sluzi kao pointer na aktivni log. Ovo je minimalan kompromis sa mutable stanjem u okviru funkcionalnog programiranja.

---

## 3. Opis koriscenih tehnologija

- **Clojure** - funkcionalni programski jezik na JVM platformi
- **Leiningen** - build alat i upravljac zavisnostima za Clojure projekte
- **clojure.tools.cli** - biblioteka za parsiranje argumenata komandne linije
- **Midje** - testing framework za Clojure
- **Java IO** - za efikasan rad sa fajlovima

---

## 4. Korisnicko uputstvo

### Pokretanje aplikacije
```bash
lein midje ## run tests
lein run
```

### Dostupne komande
```
clogjure> index logs/app.log    # Kreiranje index-a nad log fajlom
clogjure> ls                    # Izlistavanje svih dostupnih indeksa
clogjure> use app-inverted.idx  # Ucitava postojeci index fajl po imenu
clogjure> status                # Pokazuje koji index je trenutno ucitan
clogjure> search error          # Pretrazuje ucitani indeks (AND je default logika za pretragu)
clogjure> clear                 # Cisti ekran
clogjure> exit                  # Izlaz iz programa
```

### Primeri:
```
clogjure> search error                     # Linije koje sadrze rec error
clogjure> search error memory              # Linije koje sadrze rec error i rec memory
clogjure> search error warning --any       # Linije koje sadrze rec error ili sadrze rec warning
clogjure> search err --prefix              # Linije koje sadrze reci sa prefixom err
clogjure> search error --from 2026-01-19T10:00:00 --to 2026-01-19T12:00:00 # Linije koje sadrze rec error i nalaze se u datom vremenskom opsegu
```

---

## 5. Prilog

### Struktura projekta

```
cLOGjure/
├── src/clogjure/
│   ├── core.clj        # CLI interfejs, komande
│   ├── index.clj       # Kreiranje, ucitavanje i cuvanje indeksa
│   ├── search.clj      # Algoritmi pretrage (AND, OR, prefix)
│   ├── state.clj       # Globalno stanje sesije (atom)
│   └── util.clj        # Parsiranje timestamp formata
├── test/clogjure/
│   ├── index_test.clj  # Testovi za indeks funkcije
│   ├── search_test.clj # Testovi za pretragu
│   └── util_test.clj   # Testovi za util funkcije
├── resources/
│   ├── config/         # Konfiguracija timestamp formata
│   ├── logs/           # Test log fajlovi
│   └── indexes/        # Sacuvani indeksi
└── project.clj         # Leiningen konfiguracija
```

### Reference

[1] Elastic Stack (ELK Stack) - https://www.elastic.co/elastic-stack

[2] Apache Lucene - https://lucene.apache.org/

[3] GNU Grep - https://www.gnu.org/software/grep/
