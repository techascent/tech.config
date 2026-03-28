(ns tech.config.files
  "Node.js file discovery: reads *-config.edn from a filesystem directory."
  (:require ["fs" :as fs]
            ["path" :as path]
            [cljs.reader :as edn]
            [clojure.string :as str]))

(def ^:private config-dir
  (or (.. js/process -env -CONFIG_DIR) "resources"))

(defn config-file-maps
  "Returns an unordered seq of [filename config-map] pairs from
  the config directory."
  []
  (when (.existsSync fs config-dir)
    (->> (.readdirSync fs config-dir)
         js->clj
         (filter #(str/ends-with? % "config.edn"))
         (map (fn [filename]
                [filename
                 (edn/read-string
                  (.readFileSync fs (path/join config-dir filename) "utf8"))])))))
