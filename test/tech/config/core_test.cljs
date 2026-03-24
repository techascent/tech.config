(ns tech.config.core-test
  (:require [cljs.test :refer [deftest is testing]]
            [tech.config.core :as config]))

(deftest get-config-test
  (testing "Config returns values from edn files"
    (is (some? (config/get-config-map)))
    (is (map? (config/get-config-map)))))

(deftest missing-config-throws
  (testing "Missing config key throws"
    (is (thrown? js/Error (config/get-config :some-bs-val)))))

(deftest get-config-with-default
  (testing "get-config with default returns default for missing keys"
    (is (= "fallback" (config/get-config :nonexistent "fallback")))))

(deftest set-config-test
  (testing "set-config! sets and returns old value"
    (let [old (config/set-config! :test-key "hello")]
      (is (= "hello" (config/get-config :test-key)))
      (config/set-config! :test-key old))))

(deftest config-table-str-test
  (testing "get-config-table-str returns a string"
    (is (string? (config/get-config-table-str)))))

(deftest unchecked-get-config-test
  (testing "unchecked-get-config returns nil for missing keys"
    (is (nil? (config/unchecked-get-config :nonexistent-key)))))
