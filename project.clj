(defproject techascent/tech.config "0.3.12"
  :description "A configuration library."
  :url "http://github.com/techascent/tech.config"

  :min-lein-version "2.8.1"

  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [environ "1.1.0"]
                 [org.clojure/java.classpath "0.3.0"]]

  :plugins [[lein-environ "1.1.0"]]
  :profiles {:test {:resource-paths ["test/resources"]
                    :env {:overwrite "80"
                          :env-config-overwrite "true"
                          :complex-type-env-overwrite-map "{:a 1 :b 3}"
                          :complex-type-env-overwrite-vec "[:c :b :a]"
                          :complex-type-env-overwrite-seq "(:c :b :a)"}}})
