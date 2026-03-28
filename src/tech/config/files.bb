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
  (let [cp-dirs (->> (str/split (System/getProperty "java.class.path") #":")
                     (map #(File. ^String %))
                     (filter #(.isDirectory ^File %)))
        cp-set  (set (map #(.getCanonicalPath ^File %) cp-dirs))
        ;; CONFIG_DIR allows scripts without bb.edn to specify a config directory
        extra   (when-let [f (some-> (System/getenv "CONFIG_DIR") (File.))]
                  (when (and (.isDirectory f) (not (cp-set (.getCanonicalPath f))))
                    [f]))]
    (concat cp-dirs extra)))

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
