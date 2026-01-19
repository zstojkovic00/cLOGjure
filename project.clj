(defproject clogjure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.3.250"]]
  :plugins [[lein-cljfmt "0.9.2"]]
  :main clogjure.core
  :repl-options {:init-ns clogjure.core}
  :profiles {:dev {:dependencies [[midje "1.10.10"]]
                   :plugins [[lein-midje "3.2.1"]]}})
