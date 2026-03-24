(ns tech.config.core
  (:require ["fs" :as fs]
            ["path" :as path]
            [cljs.reader :as edn]
            [clojure.string :as str]
            [tech.config.environ :refer [read-env]]))

(def ^:private config-dir
  (or (.. js/process -env -CONFIG_DIR) "resources"))

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
              (let [d (get dest-map k)]
                (->> (cond
                       (nil? d) v
                       (string? d) (str v)
                       (number? d) (if (string? v)
                                     (let [n (js/parseFloat v)]
                                       (if (js/isNaN n)
                                         (throw (ex-info (str "Config value: " v " should be a number.") {:key k}))
                                         n))
                                     v)
                       (keyword? d) (if (and (string? v) (not-empty v) (= (first v) ":"))
                                      (keyword (subs v 1))
                                      (keyword v))
                       (boolean? d) (cond
                                      (boolean? v) v
                                      (string? v) (= (str/lower-case v) "true")
                                      :else (boolean v))
                       (map? d) (cond
                                  (map? v) v
                                  (string? v) (let [x (edn/read-string v)]
                                                (if (map? x) x
                                                    (throw (ex-info (str "Config value: " v " should be a map.") {:key k}))))
                                  :else (throw (ex-info (str "Config value: " v " should be a map.") {:key k})))
                       (vector? d) (cond
                                     (vector? v) v
                                     (string? v) (let [x (edn/read-string v)]
                                                   (if (vector? x) x
                                                       (throw (ex-info (str "Config value: " v " should be a vector.") {:key k}))))
                                     :else (throw (ex-info (str "Config value: " v " should be a vector.") {:key k})))
                       :else (if (string? v)
                               (edn/read-string v)
                               v))
                     (assoc dest-map k)))))
          dest
          src))

(defn- read-files
  "Reads *-config.edn files from the config directory, sorted reverse
  alphabetically with app-config and user-config taking precedence."
  []
  (if-not (.existsSync fs config-dir)
    [{} {} #{}]
    (let [move-to-end-fn (fn [entry coll]
                           (let [grouped (group-by #(= (first %) entry) coll)]
                             (concat (get grouped false) (get grouped true))))]
      (->> (.readdirSync fs config-dir)
           js->clj
           (filter #(str/ends-with? % "config.edn"))
           sort
           reverse
           (map (fn [filename] [filename (path/join config-dir filename)]))
           (move-to-end-fn "app-config.edn")
           (move-to-end-fn "user-config.edn")
           (reduce (fn [[cfg src keys] [filename full-path]]
                     (let [file-map (edn/read-string (.readFileSync fs full-path "utf8"))
                           file-keys (cljs.core/keys file-map)]
                       [(coercing-merge cfg file-map)
                        (merge src (into {} (map #(vector % filename) file-keys)))
                        (into keys file-keys)]))
                   [{} {} #{}])))))

(defonce ^:private state
  (let [[file-cfg file-src file-keys] (read-files)
        env     (read-env)
        env-src (into {} (map #(vector % "environment") (keys env)))]
    (atom {:config    (coercing-merge file-cfg env)
           :sources   (merge file-src env-src)
           :file-keys (into file-keys (keys file-cfg))})))

(defn get-config-map
  []
  (:config @state))

(defn reload-config!
  "Refreshes the config (e.g. re-reading .edn files)"
  []
  (let [[file-cfg file-src file-keys] (read-files)
        env     (read-env)
        env-src (into {} (map #(vector % "environment") (keys env)))]
    (reset! state {:config    (coercing-merge file-cfg env)
                   :sources   (merge file-src env-src)
                   :file-keys (into file-keys (keys file-cfg))})))

(defn get-configurable-options
  "Returns all keys specified in .edn files."
  []
  (:file-keys @state))

(defn unchecked-get-config
  "Get app config. Unlike `get-config`, can return nil for missing config."
  [k]
  (get (:config @state) k))

(defn get-config
  "Get app config. Accepts a key such as :port."
  ([k]
   (let [v (unchecked-get-config k)]
     (when (nil? v)
       (throw (ex-info (str "Missing config value: " k) {:key k})))
     v))
  ([k default]
   (get (:config @state) k default)))

(defn set-config!
  "Very dangerous, but useful during testing. Set a config value."
  [key value]
  (let [old-val (unchecked-get-config key)]
    (swap! state assoc-in [:config key] value)
    old-val))

(defn- sensitive? [k]
  (let [s (name k)]
    (some #(str/includes? s %) ["secret" "private" "password" "token"])))

(defn get-config-table-str
  "Returns a string representation of the current config map."
  [& {:keys [no-redact redact-keys]
      :or {no-redact false}}]
  (let [{:keys [config sources file-keys]} @state
        col (fn [w s] (let [s (str s)] (str s (apply str (repeat (max 1 (- w (count s))) " ")))))
        rows (->> file-keys sort
                  (map (fn [k] {:key k
                                :value (get config k)
                                :source (get sources k "unknown")})))]
    (str (col 30 "Key") (col 30 "Value") "Source\n"
         (apply str (repeat 80 "-")) "\n"
         (apply str
                (map (fn [{:keys [key value source]}]
                       (let [print-value (cond (and (not no-redact)
                                                    redact-keys
                                                    ((set redact-keys) key))
                                               "[REDACTED]"
                                               (and (not no-redact)
                                                    (not redact-keys)
                                                    (sensitive? key))
                                               "[REDACTED]"
                                               (string? value) (str "\"" value "\"")
                                               :else value)]
                         (str (col 30 key) (col 30 print-value) source "\n")))
                     rows)))))

(defn print-config
  "Prints the current config table."
  []
  (println (get-config-table-str)))
