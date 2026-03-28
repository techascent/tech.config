(ns tech.config.files
  "Babashka file discovery: classpath directories only (no jar scanning)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]))

(defn- config-file? [^File f]
  (and (.isFile f)
       (.contains (.getName f) "config.edn")))

(defn- classpath-directories []
  (->> (str/split (System/getProperty "java.class.path") #":")
       (map #(File. ^String %))
       (filter #(.isDirectory ^File %))))

(defn config-file-maps
  "Returns an unordered seq of [filename config-map] pairs from
  classpath directories."
  []
  (->> (classpath-directories)
       (mapcat file-seq)
       (filter config-file?)
       (map (fn [^File f]
              [(.getName f)
               (with-open [rdr (io/reader f)]
                 (edn/read (java.io.PushbackReader. rdr)))]))))
