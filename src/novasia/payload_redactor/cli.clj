(ns novasia.payload-redactor.cli
  (:require [novasia.payload-redactor :as redactor])
  (:import [java.nio.file Files Paths StandardOpenOption]
           [java.nio.charset StandardCharsets]))

(defn- path [s] (.normalize (.toAbsolutePath (Paths/get s (make-array String 0)))))
(defn- read-input [s]
  (let [p (path s)]
    (when (> (Files/size p) 2000000) (throw (Exception.)))
    (redactor/read-safe (Files/readString p StandardCharsets/UTF_8))))
(defn- write-new [p value]
  (Files/writeString p (str (pr-str value) "\n") StandardCharsets/UTF_8
                     (into-array StandardOpenOption [StandardOpenOption/CREATE_NEW StandardOpenOption/WRITE])))
(defn -main [& args]
  (try
    (when-not (<= 4 (count args) 5) (throw (Exception.)))
    (let [[policy input output audit] args
          out (path output) log (path audit)]
      (when (or (= out log) (Files/exists out (make-array java.nio.file.LinkOption 0))
                (Files/exists log (make-array java.nio.file.LinkOption 0))) (throw (Exception.)))
      (let [result (redactor/redact-payload (read-input policy) (read-input input)
                                          (if (= 5 (count args)) (read-input (nth args 4)) {}))]
        (write-new out (:payload result))
        (write-new log (:audit result))
        (println "Draft payload and value-free audit created. Human review is required.")))
    (catch Exception _
      (binding [*out* *err*] (println "Cannot process input or create outputs; check the contract and paths."))
      (System/exit 2))))
