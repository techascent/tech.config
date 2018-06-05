(defproject techascent/tech.config "0.3.5-SNAPSHOT"
  :description "A configuartion library."
  :url "http://github.com/thinktopic/think.config"

  :plugins [[lein-environ "1.1.0"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [environ "1.1.0"]
                 [org.clojure/java.classpath "0.3.0"]]

  :profiles {:test {:resource-paths ["test/resources"]
                    :env {:overwrite "80"
                          :env-config-overwrite "true"
                          :complex-type-env-overwrite-map "{:a 1 :b 3}"
                          :complex-type-env-overwrite-vec "[:c :b :a]"
                          :complex-type-env-overwrite-seq "(:c :b :a)"}}})
