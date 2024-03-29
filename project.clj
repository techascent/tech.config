(defproject techascent/tech.config "0.3.16-SNAPSHOT"
  :description "A configuration library."
  :url "http://github.com/techascent/tech.config"
  :license {:name "MIT"
            :comment "MIT License"
            :url "https://choosealicense.com/licenses/mit"
            :year 2024
            :key "mit"}

  :min-lein-version "2.8.1"

  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [org.clojure/java.classpath "1.0.0"]]

  :plugins [[lein-environ "1.1.0"]]
  :profiles {:test {:resource-paths ["test/resources"]
                    :env {:overwrite "80"
                          :env-config-overwrite "true"
                          :complex-type-env-overwrite-map "{:a 1 :b 3}"
                          :complex-type-env-overwrite-vec "[:c :b :a]"
                          :complex-type-env-overwrite-seq "(:c :b :a)"}}})
