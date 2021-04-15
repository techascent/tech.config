(ns tech.config.core
  (:require [environ.core :refer [env]]
            [clojure.java.classpath :as cp]
            [clojure.set :as set]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

(def ^:dynamic *config-map* nil)
(def ^:dynamic *config-sources* {})
(def ^:dynamic *config-keys* #{}) ;; Only keys found in config _files_.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper functions
(defn- input-stream-to-map
  "Helper function for reading a classpath jar into a map."
  [stream]
  (with-open [^java.io.BufferedReader s stream]
    (edn/read (java.io.PushbackReader. s))))

(defn- is-config-file?
  [^java.io.File file]
  (.contains (.getName file) "config.edn"))

(defn- is-config-jarentry?
  [^java.util.jar.JarEntry entry]
  (.contains (.getName entry) "config.edn"))

(defn- coercing-merge
  "Takes two maps and merges the source into the dest while trying to coerce
  values into the type of the destination map. This is so that a base config
  (e.g. in a library) can specify types and they can be overwritten by strings
  from the outside (e.g. either via the command line or the environment) and
  have those values become the correct type."
  [dest src]
  (reduce (fn [dest-map [k v]]
            (if-not (contains? dest-map k)
              (assoc dest-map k v)
              (let [d (get dest-map k)
                    bad-type-fn (fn [x type_]
                                  (->> (format "Config value: %s should be a %s." x type_)
                                       (IllegalArgumentException.)
                                       (throw)))]
                (->> (cond
                       (nil? d) v
                       (string? d) (str v)
                       (float? d) (Double. (str v))
                       (integer? d) (Integer. (str v))
                       (keyword? d) (if (and (string? v) (not-empty v) (= \: (first v)))
                                      (keyword (subs v 1))
                                      (keyword v))
                       (instance? Boolean d) (boolean (Boolean. (str v)))
                       (map? d) (cond
                                  (map? v) v
                                  (string? v) (->> (edn/read-string v)
                                                   ((fn [x] (if (map? x) x
                                                              (bad-type-fn x "map")))))
                                  :default (bad-type-fn v "map"))
                       (seq? d) (cond
                                  (seq? v) v
                                  (string? v) (->> (edn/read-string v)
                                                   ((fn [x] (if (seq? x) x
                                                              (bad-type-fn x "seq")))))
                                  :default (bad-type-fn v "seq"))
                       (vector? d) (cond
                                     (vector? v) v
                                     (string? v) (->> (edn/read-string v)
                                                      ((fn [x] (if (vector? x) x
                                                                 (bad-type-fn x "seq")))))
                                     :default (bad-type-fn v "seq"))
                       :default (edn/read-string  v))
                     (assoc dest-map k)))))
          dest
          src))

(defn- get-config-streams
  "Returns BufferedReaders for *-config.edn files found in jar-files."
  [jar-files]
  (->> jar-files
       (map (fn [^java.util.jar.JarFile jarfile]
              [jarfile
               (filter is-config-jarentry?
                       (enumeration-seq (.entries jarfile)))]))
       (filter #(> (count (second %)) 0))
       ;; Flatten the config map (make sure that all of the values map to their source)
       ((fn [m]
          (for [[k v] m
                val_ v]
            [k val_])))
       (mapv (fn [[^java.util.jar.JarFile jarfile ^java.util.jar.JarEntry jarentry]]
           [(str (.getName jarfile) "/" (.getName jarentry))
            (io/reader (.getInputStream jarfile jarentry))]))))

(defn file-config
  "Loops through all of the .edn files in the jars as well as resources and
  coerce-merges them reverse alphabetically with app-config and user-config
  taking precedence over the remaining, respectively."
  []
  (let [short-name-fn (partial re-find #"[^\/]+$")
        move-to-end-fn (fn [entry coll]
                         (let [m (group-by #(= (short-name-fn (first %)) entry) coll)]
                           (concat (get m false) (get m true))))
        update-config-sources? (empty? *config-sources*)]
    (->> (cp/classpath-directories)
         (map file-seq)
         (flatten)
         (filter is-config-file?)
         (map (fn [^java.io.File f] [(.getName f) (io/reader f)]))
         (concat (get-config-streams (cp/classpath-jarfiles)))
         (sort-by (comp short-name-fn first))
         (reverse)
         (move-to-end-fn "app-config.edn")
         (move-to-end-fn "user-config.edn")
         (reduce (fn [eax [path file]]
                   (let [m (input-stream-to-map file)]
                     (when update-config-sources?
                       (doseq [[k _] m]
                         (alter-var-root #'*config-keys* #(conj % k))
                         (alter-var-root #'*config-sources* #(assoc % k (short-name-fn path)))))
                     (coercing-merge eax m))) {}))))

(defn- build-config
  "Squashes the environment onto the config-*.edn files."
  ([config-map]
   (let [final-map (coercing-merge config-map env)
         print-map (->> final-map
                        (filter #(*config-keys* (first %)))
                        (into {}))]
     (doseq [k (set/intersection (set (keys env))
                                 (set (keys print-map)))]
       (alter-var-root #'*config-sources* #(assoc % k "environment")))
     final-map))
  ([]
   (build-config (file-config))))

(defn get-config-map
  []
  (when-not *config-map*
    (alter-var-root #'*config-map* (fn [_] (build-config))))
  *config-map*)

(defn reload-config!
  "Refreshes the config (e.g. re-reading .edn files)"
  []
  (alter-var-root #'*config-map* (fn [_] nil)))


(defmacro static-configuration
  "Macro meant to be used during AOT compile to define the jar and classpath based
  configuration once into a variable.

  Example:

```clojure
  (def static-config (config/static-configuration))
```
  "
  []
  (let [config-map (file-config)
        config-sources *config-sources*
        config-keys *config-keys*]
    `{:config-sources ~config-sources
      :config-keys ~config-keys
      :config-map ~config-map}))


(defn set-static-configuration!
  "Given a map of static configuration information, combine with environment variables
  and set the config global vars.  The outcome of this should be identical to
  calling (build-config) when no config information has been requested yet.

  This is meant to be used with the `static-configuration` macro."
  [static-config]
  (alter-var-root #'*config-map* (constantly (build-config (:config-map static-config))))
  (alter-var-root #'*config-keys* (constantly (:config-keys static-config)))
  (alter-var-root #'*config-sources* (constantly (:config-sources static-config)))
  :ok)


(defn get-configurable-options
  "This function returns all keys that are specified in .edn files, excluding
  the automatic variables such as os-*."
  []
  (-> (get-config-map)
      (keys)
      (set)
      (set/difference #{:os-arch :os-name :os-version})))

(defn get-config-table-str
  "Returns a nice string representation of the current config map."
  [& {:keys [no-redact redact-keys]
      :or {no-redact false}}]
  (let [table (->> (get-config-map)
                   (filter #(*config-keys* (first %)))
                   (sort-by first)
                   (map (fn [[k v]]
                          {:key k
                           :value v
                           :source (get *config-sources* k)})))
        key-width (min 30 (apply max (map (comp count str :key) table)))
        val-width (min 30 (apply max (map (comp count str :value) table)))
        src-width (min 20 (apply max (map (comp count str :source) table)))]
    (with-out-str
      (with-bindings {#'pprint/*print-right-margin* nil}
        (println (format (str "%-" key-width "s %-" val-width "s %-" src-width "s") "Key" "Value" "Source"))
        (println (apply str (repeat (+ key-width val-width src-width 2) "-")))
        (doseq [{:keys [key value source]} table]
          (let [print-value (cond (and (not no-redact)
                                       redact-keys
                                       ((set redact-keys) key))
                                  "[REDACTED]"
                                  (and (not no-redact)
                                       (not redact-keys)
                                       (or (.contains (str key) "secret")
                                           (.contains (str key) "private")))
                                  "[REDACTED]"
                                  :default (if (string? value)
                                             (format "\"%s\"" value)
                                             value))]
            (println (format (str "%-" key-width "s %-" val-width "s %-" src-width "s")
                             key print-value source))))))))

(defn unchecked-get-config
  "Get app config. Unlike `get-config`, doesn't coerce arguments and can return nil for missing config."
  [k]
  ((get-config-map) k))

(defn get-config
  "Get app config. Accepts a key such as \"PORT\" or :port."
  ([k]
   (let [retval (unchecked-get-config k)]
     (when (nil? retval)
       (throw (IllegalArgumentException. (format "Missing config value: %s" k))))
     retval))
  ([k read-string?]
   (let [raw-config (get-config k)]
     (if (and (string? raw-config) read-string?)
       (edn/read-string raw-config)
       raw-config))))

(defn set-config!
  "Very dangerous, but useful during testing. Set a config value.
  See: `with-config`"
  [key value]
  (let [old-map (get-config-map)]
    (alter-var-root #'*config-map* assoc key value)
    (old-map key)))

(defmacro with-config
  [config-key-vals & body]
  `(let [new-map# (#'tech.config.core/coercing-merge (get-config-map) (apply hash-map ~config-key-vals))
         new-keys# (take-nth 2 ~config-key-vals)
         new-sources# (->> new-keys#
                           (map (fn [new-var#] [new-var# "with-config"]))
                           (into {}))]
     (with-bindings {#'*config-map* new-map#
                     #'*config-sources* (merge *config-sources* new-sources#)
                     #'*config-keys* (set/union *config-keys* (set new-keys#))}
       ~@body)))
