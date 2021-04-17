(defproject techascent/tech.config "0.3.10-SNAPSHOT"
  :description "A configuration library."
  :url "http://github.com/techascent/tech.config"

  :min-lein-version "2.8.1"

  :plugins [[lein-environ "1.1.0"]
            [lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}


  :profiles {:test {:resource-paths ["test/resources"]
                    :env {:overwrite "80"
                          :env-config-overwrite "true"
                          :complex-type-env-overwrite-map "{:a 1 :b 3}"
                          :complex-type-env-overwrite-vec "[:c :b :a]"
                          :complex-type-env-overwrite-seq "(:c :b :a)"}}})
