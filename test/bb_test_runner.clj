(ns bb-test-runner
  (:require [clojure.test :as t]))

;; Set system properties that the JVM tests get via -D flags
(System/setProperty "overwrite" "80")
(System/setProperty "env-config-overwrite" "true")
(System/setProperty "complex-type-env-overwrite-map" "{:a 1 :b 3}")
(System/setProperty "complex-type-env-overwrite-vec" "[:c :b :a]")
(System/setProperty "complex-type-env-overwrite-seq" "(:c :b :a)")

(require 'tech.config.config-test)

(let [{:keys [fail error]} (t/run-tests 'tech.config.config-test)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
