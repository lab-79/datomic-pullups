(defproject lab79/datomic-pullups "0.1.0"
  :description "Union and intersection for Datomic pull syntax"
  :url "https://github.com/lab-79/datomic-pullups"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:plugins [[lein-cloverage "1.0.7-SNAPSHOT"]
                             [jonase/eastwood "0.2.3"]
                             [lein-kibit "0.1.3"]]}}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]])
