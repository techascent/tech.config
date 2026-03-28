(ns tech.config.core
  (:require [tech.config.environ :refer [read-env]]
            [tech.config.files :as files]
            #?(:clj [clojure.set :as set])
            #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            #?(:clj [clojure.pprint :as pprint])
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Platform helpers — tiny wrappers over parsing differences

(defn- numeric? [v]
  #?(:clj  (or (float? v) (integer? v))
     :cljs (number? v)))

(defn- parse-number
  "Parses v as a number, preserving the type of base (int vs float) on JVM."
  [v base]
  #?(:clj  (if (integer? base)
             (Integer/parseInt (str v))
             (Double/parseDouble (str v)))
     :cljs (let [n (js/parseFloat (str v))]
             (if (js/isNaN n)
               (throw (ex-info (str "Config value should be a number: " v) {:value v}))
               n))))

(defn- parse-bool
  "Parses v as a boolean."
  [v]
  #?(:clj  (Boolean/parseBoolean (str v))
     :cljs (cond
             (boolean? v) v
             (string? v) (= (str/lower-case v) "true")
             :else (boolean v))))

(defn- char-colon
  "The colon character, platform-appropriate for comparison with (first s)."
  []
  #?(:clj \: :cljs ":"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Coercing merge — fully cross-platform

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
                    bad-type (fn [x type_]
                               (throw (ex-info (str "Config value: " x " should be a " type_ ".")
                                               {:key k :value x :expected-type type_})))]
                (->> (cond
                       (nil? d) v
                       (string? d) (str v)
                       (numeric? d) (parse-number v d)
                       (keyword? d) (if (and (string? v) (not-empty v) (= (first v) (char-colon)))
                                      (keyword (subs v 1))
                                      (keyword v))
                       (boolean? d) (parse-bool v)
                       (map? d) (cond
                                  (map? v) v
                                  (string? v) (let [x (edn/read-string v)]
                                                (if (map? x) x (bad-type x "map")))
                                  :else (bad-type v "map"))
                       (sequential? d) (cond
                                         (sequential? v) v
                                         (string? v) (let [x (edn/read-string v)]
                                                       (if (sequential? x) x (bad-type x "sequential")))
                                         :else (bad-type v "sequential"))
                       :else (if (string? v) (edn/read-string v) v))
                     (assoc dest-map k)))))
          dest
          src))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; File config — shared ordering, delegates discovery to tech.config.files

(defn- order-config-files
  "Sorts config file pairs reverse-alphabetically with app-config and
  user-config taking precedence (applied last)."
  [file-pairs]
  (let [short-name (partial re-find #"[^\/]+$")
        move-to-end (fn [entry coll]
                      (let [m (group-by #(= (short-name (first %)) entry) coll)]
                        (concat (get m false) (get m true))))]
    (->> file-pairs
         (sort-by (comp short-name first))
         reverse
         (move-to-end "app-config.edn")
         (move-to-end "user-config.edn"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Config state — JVM/bb use dynamic vars, CLJS uses atom

#?(:clj (def ^:dynamic *config-map* nil))
#?(:clj (def ^:dynamic *config-sources* {}))
#?(:clj (def ^:dynamic *config-keys* #{}))

(defn- file-config
  "Reads and merges all config files, tracking sources and keys."
  []
  (let [ordered (order-config-files (files/config-file-maps))]
    #?(:clj
       (reduce (fn [cfg [path file-map]]
                 (let [short-name (re-find #"[^\/]+$" path)]
                   (doseq [[k _] file-map]
                     (alter-var-root #'*config-keys* conj k)
                     (alter-var-root #'*config-sources* assoc k short-name))
                   (coercing-merge cfg file-map)))
               {} ordered)

       :cljs
       (reduce (fn [[cfg src keys] [filename file-map]]
                 [(coercing-merge cfg file-map)
                  (reduce (fn [s k] (assoc s k filename)) src (cljs.core/keys file-map))
                  (into keys (cljs.core/keys file-map))])
               [{} {} #{}] ordered))))

(defn- build-config
  "Squashes the environment onto the config files."
  #?(:clj
     ([config-map]
      (let [env (read-env)
            final-map (coercing-merge config-map env)
            print-map (->> final-map
                           (filter #(*config-keys* (first %)))
                           (into {}))]
        (doseq [k (set/intersection (set (keys env))
                                    (set (keys print-map)))]
          (alter-var-root #'*config-sources* assoc k "environment"))
        final-map))
     :cljs
     ([config-map env-src-map]
      (let [env (read-env)]
        [(coercing-merge config-map env)
         (reduce (fn [s k] (assoc s k "environment")) env-src-map (keys env))])))
  ([]
   #?(:clj  (build-config (file-config))
      :cljs (let [[cfg src keys] (file-config)
                  [final-cfg final-src] (build-config cfg src)]
              [final-cfg final-src keys]))))

#?(:cljs
   (defonce ^:private state
     (let [[cfg src keys] (build-config)]
       (atom {:config cfg :sources src :file-keys keys}))))

(defn get-config-map []
  #?(:clj  (do (when-not *config-map*
                 (alter-var-root #'*config-map* (fn [_] (build-config))))
               *config-map*)
     :cljs (:config @state)))

(defn reload-config!
  "Refreshes the config (e.g. re-reading .edn files)"
  []
  #?(:clj  (alter-var-root #'*config-map* (fn [_] nil))
     :cljs (let [[cfg src keys] (build-config)]
             (reset! state {:config cfg :sources src :file-keys keys}))))

(defn get-configurable-options
  "Returns all keys specified in .edn files."
  []
  #?(:clj  (-> (get-config-map) keys set (set/difference #{:os-arch :os-name :os-version}))
     :cljs (:file-keys @state)))

(defn unchecked-get-config
  "Get app config. Unlike `get-config`, can return nil for missing config."
  [k]
  (get (get-config-map) k))

(defn get-config
  "Get app config. Accepts a key such as :port.
  Single arity throws if key is missing. Two-arity returns default."
  ([k]
   (let [v (unchecked-get-config k)]
     (when (nil? v)
       (throw (ex-info (str "Missing config value: " k) {:key k})))
     v))
  ([k default]
   (let [v (unchecked-get-config k)]
     (if (nil? v) default v))))

(defn set-config!
  "Very dangerous, but useful during testing. Set a config value."
  [key value]
  (let [old-val (unchecked-get-config key)]
    #?(:clj  (alter-var-root #'*config-map* assoc key value)
       :cljs (swap! state assoc-in [:config key] value))
    old-val))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Config display

(defn- sensitive? [k]
  (let [s (str k)]
    (or (str/includes? s "secret")
        (str/includes? s "private")
        (str/includes? s "password")
        (str/includes? s "token"))))

(defn get-config-table-str
  "Returns a string representation of the current config map."
  [& {:keys [no-redact redact-keys]
      :or {no-redact false}}]
  (let [#?@(:clj  [config-keys *config-keys*
                   sources *config-sources*]
            :cljs [config-keys (:file-keys @state)
                   sources (:sources @state)])
        config (get-config-map)
        rows (->> config-keys sort
                  (map (fn [k] {:key k :value (get config k) :source (get sources k "unknown")})))
        col (fn [w s] (let [s (str s)] (str s (apply str (repeat (max 1 (- w (count s))) " ")))))]
    (str (col 30 "Key") (col 30 "Value") "Source\n"
         (apply str (repeat 80 "-")) "\n"
         (apply str
                (map (fn [{:keys [key value source]}]
                       (let [pv (cond (and (not no-redact) redact-keys ((set redact-keys) key)) "[REDACTED]"
                                      (and (not no-redact) (not redact-keys) (sensitive? key)) "[REDACTED]"
                                      (string? value) (str "\"" value "\"")
                                      :else value)]
                         (str (col 30 key) (col 30 pv) source "\n")))
                     rows)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JVM-only features

#?(:clj
   (defmacro static-configuration
     "Macro meant to be used during AOT compile to define the jar and classpath
  based configuration once into a variable."
     []
     (let [config-map (file-config)
           config-sources *config-sources*
           config-keys *config-keys*]
       `{:config-sources ~config-sources
         :config-keys ~config-keys
         :config-map ~config-map})))

#?(:clj
   (defn set-static-configuration!
     "Given a map of static configuration information, combine with environment
  variables and set the config global vars."
     [static-config]
     (alter-var-root #'*config-map* (constantly (build-config (:config-map static-config))))
     (alter-var-root #'*config-keys* (constantly (:config-keys static-config)))
     (alter-var-root #'*config-sources* (constantly (:config-sources static-config)))
     :ok))

#?(:clj
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
          ~@body))))
