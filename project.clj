(defproject lab79/datomic-pullups "1.0.1"
  :description "Union and intersection for Datomic pull syntax"
  :url "https://github.com/lab-79/datomic-pullups"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:plugins [[lein-cloverage "1.0.9"]
                             [jonase/eastwood "0.2.4"]
                             [lein-kibit "0.1.5"]]}}
  :dependencies [[org.clojure/clojure "1.9.0-alpha19"]])
