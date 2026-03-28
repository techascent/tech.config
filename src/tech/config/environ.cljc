(ns tech.config.environ
  "Reads environment variables and system properties into a config map."
  (:require #?(:cljs [goog.object :as obj])
            [clojure.string :as str]))

#?(:cljs (def ^:private nodejs?
           (exists? js/require)))

#?(:cljs (def ^:private process
           (when nodejs? (js/require "process"))))

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn- read-system-env []
  (->> #?(:clj (System/getenv)
          :cljs (zipmap (obj/getKeys (.-env process))
                        (obj/getValues (.-env process))))
       (map (fn [[k v]] [(keywordize k) v]))
       (into {})))

#?(:clj (defn- read-system-props []
          (->> (System/getProperties)
               (map (fn [[k v]] [(keywordize k) v]))
               (into {}))))

(defn- warn-on-overwrite [ms]
  (doseq [[k kvs] (group-by key (apply concat ms))
          :let  [vs (map val kvs)]
          :when (and (next kvs) (not= (first vs) (last vs)))]
    (println "Warning: environ value" (first vs) "for key" k
             "has been overwritten with" (last vs))))

(defn- merge-env [& ms]
  (warn-on-overwrite ms)
  (apply merge ms))

(defn read-env []
  #?(:clj (merge-env
           (read-system-env)
           (read-system-props))
     :cljs (if nodejs?
             (read-system-env)
             {})))
