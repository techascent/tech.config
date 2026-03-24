(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'techascent/tech.config)
(def version (-> (slurp "VERSION") clojure.string/trim))
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :resource-dirs ["resources"]
                :scm {:url "https://github.com/techascent/tech.config"
                      :tag version}
                :pom-data [[:licenses
                            [:license
                             [:name "MIT"]
                             [:url "https://choosealicense.com/licenses/mit"]]]]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
           :jar-file jar-file}))
