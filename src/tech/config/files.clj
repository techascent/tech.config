(ns tech.config.files
  "JVM file discovery: classpath directories + jar scanning."
  (:require [clojure.java.classpath :as cp]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.util.jar JarFile JarEntry]))

(defn- config-file? [^File f]
  (and (.isFile f)
       (.contains (.getName f) "config.edn")))

(defn- config-jarentry? [^JarEntry entry]
  (.contains (.getName entry) "config.edn"))

(defn- read-edn-stream [stream]
  (with-open [^java.io.BufferedReader s stream]
    (edn/read (java.io.PushbackReader. s))))

(defn- dir-config-files
  "Returns [filename config-map] pairs from classpath directories."
  []
  (->> (cp/system-classpath)
       (filter #(.isDirectory ^File %))
       (mapcat file-seq)
       (filter config-file?)
       (map (fn [^File f]
              [(.getName f) (read-edn-stream (io/reader f))]))))

(defn- jar-config-files
  "Returns [filename config-map] pairs from jars on the classpath."
  []
  (let [jars (->> (cp/system-classpath)
                  (filter cp/jar-file?)
                  (map #(JarFile. ^File %)))]
    (->> jars
         (mapcat (fn [^JarFile jarfile]
                   (->> (enumeration-seq (.entries jarfile))
                        (filter config-jarentry?)
                        (map (fn [^JarEntry entry]
                               [(str (.getName jarfile) "/" (.getName entry))
                                (read-edn-stream
                                 (io/reader (.getInputStream jarfile entry)))])))))
         vec)))

(defn config-file-maps
  "Returns an unordered seq of [filename config-map] pairs from all
  classpath directories and jars."
  []
  (concat (dir-config-files) (jar-config-files)))
