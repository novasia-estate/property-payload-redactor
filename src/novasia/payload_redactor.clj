(ns novasia.payload-redactor
  "Explicit EDN projection with limited text/URL cleanup and value-free audit events."
  (:require [clojure.edn :as edn] [clojure.string :as str])
  (:import [java.io PushbackReader StringReader]
           [java.net URI]
           [java.nio.charset StandardCharsets]
           [java.util.regex Pattern]))

(defn- fail [] (throw (ex-info "Invalid payload, policy or options; see the input contract" {})))
(defn- utf8? [s] (.canEncode (.newEncoder StandardCharsets/UTF_8) ^CharSequence s))
(defn- finite-number? [x]
  (and (number? x) (or (not (or (instance? Double x) (instance? Float x)))
                       (and (not (Double/isNaN (double x))) (not (Double/isInfinite (double x)))))))

(defn- guard! [value]
  (let [nodes (atom 0) chars (atom 0)]
    (letfn [(walk [x depth]
              (when (or (> depth 12) (> (swap! nodes inc) 100000)) (fail))
              (cond
                (string? x) (when (or (> (count x) 100000) (not (utf8? x))
                                      (> (swap! chars + (count x)) 2000000)) (fail))
                (map? x) (do (when (> (count x) 10000) (fail))
                             (doseq [[k v] x] (walk k (inc depth)) (walk v (inc depth))))
                (coll? x) (do (when (> (bounded-count 10001 x) 10000) (fail))
                              (doseq [v x] (walk v (inc depth))))
                (or (nil? x) (boolean? x) (keyword? x) (symbol? x) (char? x) (finite-number? x)) nil
                :else (fail)))]
      (walk value 0))))

(defn read-safe
  "Read exactly one EDN value, <=2 MB UTF-8. No custom tags, #inst or #uuid."
  [raw]
  (when (or (not (string? raw)) (not (utf8? raw))
            (> (alength (.getBytes ^String raw StandardCharsets/UTF_8)) 2000000)) (fail))
  (try
    (let [eof (Object.)
          reject (fn [_] (fail))
          opts {:eof eof :readers {'inst reject 'uuid reject} :default (fn [_ _] (fail))}
          reader (PushbackReader. (StringReader. raw))
          value (edn/read opts reader)]
      (when (or (identical? value eof) (not (identical? (edn/read opts reader) eof))) (fail))
      (guard! value)
      value)
    (catch Exception _ (fail))
    (catch StackOverflowError _ (fail))))

(def ^:private leaves #{:copy-scalar :redact-text :clean-url})
(defn- policy! [p]
  (let [nodes (atom 0)]
    (letfn [(walk [x depth]
              (when (or (> depth 12) (> (swap! nodes inc) 200)) (fail))
              (cond
                (contains? leaves x) nil
                (map? x) (doseq [[k v] x]
                           (when-not (and (keyword? k) (nil? (namespace k))
                                          (re-matches #"[a-z][a-z0-9_-]{0,63}" (name k))) (fail))
                           (walk v (inc depth)))
                (and (vector? x) (= 1 (count x))) (walk (first x) (inc depth))
                :else (fail)))]
      (when-not (and (map? p) (seq p)) (fail))
      (walk p 0))))

(def ^:private email-pattern
  #"(?i)(?<![A-Z0-9._%+\-])[A-Z0-9._%+\-]{1,64}@[A-Z0-9.\-]{1,190}\.[A-Z]{2,24}(?![A-Z0-9\-])")
(def ^:private phone-pattern #"(?<![A-Za-z0-9])\+[0-9][0-9 ()\-]{5,28}[0-9](?![0-9])")
(def ^:private excluded (Object.))

(defn- event! [audit path action n]
  (when (pos? n)
    (swap! audit
           (fn [a]
             (let [updated (update-in a [:counts action] (fnil + 0) n)]
               (if (< (count (:events a)) 10000)
                 (update updated :events conj {:path path :action action :count n})
                 (update updated :omitted-event-count inc)))))))

(defn- replace-counted [value pattern replacement audit path action]
  (let [n (count (re-seq pattern value))]
    (event! audit path action n)
    (str/replace value pattern replacement)))

(defn- redact-text [value literals audit path]
  (let [after-literals
        (reduce (fn [s literal]
                  (replace-counted s (Pattern/compile (Pattern/quote literal)) "[redacted]" audit path :literal-redacted))
                value literals)
        after-email (replace-counted after-literals email-pattern "[email]" audit path :email-redacted)]
    (str/replace after-email phone-pattern
                 (fn [match]
                   (if (<= 7 (count (re-seq #"[0-9]" match)) 15)
                     (do (event! audit path :phone-redacted 1) "[phone]")
                     match)))))

(defn- clean-url [value audit path]
  (try
    (let [u (URI. value) host (.getHost u) port (.getPort u)]
      (if (and (= "https" (some-> (.getScheme u) str/lower-case))
               host (re-matches #"[A-Za-z0-9.\-]+" host)
               (or (= port -1) (<= 0 port 65535)))
        (let [output (str "https://" (str/lower-case host) (when (not= -1 port) (str ":" port))
                          (or (.getRawPath u) ""))]
          (when (not= output value) (event! audit path :url-cleaned 1))
          output)
        (do (event! audit path :url-rejected 1) excluded)))
    (catch Exception _ (event! audit path :url-rejected 1) excluded)))

(defn redact-payload
  "Project a map with an explicit policy. Result always requires human review.
  Unknown field names/values never enter audit events. Options: {:literals [...]}."
  ([policy payload] (redact-payload policy payload {}))
  ([policy payload options]
   (guard! policy) (policy! policy) (guard! payload) (guard! options)
   (when-not (and (map? payload) (map? options) (every? #{:literals} (keys options))) (fail))
   (let [literals (get options :literals [])
         audit (atom {:events [] :counts {} :omitted-event-count 0})]
     (when-not (and (vector? literals) (<= (count literals) 20)
                    (every? #(and (string? %) (<= 1 (count %) 128)) literals)) (fail))
     (let [literals (sort-by (juxt (comp - count) identity) (distinct literals))]
       (letfn [(drop-type [path] (event! audit path :type-rejected 1) excluded)
               (project [node value path]
                 (cond
                   (map? node)
                   (if (map? value)
                     (do
                       (event! audit path :unknown-fields-dropped (count (remove #(contains? node %) (keys value))))
                       (reduce (fn [out [k child]]
                                 (if (contains? value k)
                                   (let [v (project child (get value k) (conj path k))]
                                     (if (identical? excluded v) out (assoc out k v)))
                                   (do (event! audit (conj path k) :missing-field 1) out)))
                               {} (sort-by (comp name key) node)))
                     (drop-type path))
                   (vector? node)
                   (if (vector? value)
                     (mapv (fn [i v] (let [out (project (first node) v (conj path i))]
                                      (if (identical? out excluded) nil out))) (range) value)
                     (drop-type path))
                   (= node :copy-scalar)
                   (if (or (nil? value) (boolean? value) (finite-number? value) (string? value)) value (drop-type path))
                   (= node :redact-text)
                   (if (string? value) (redact-text value literals audit path) (drop-type path))
                   (= node :clean-url)
                   (if (string? value) (clean-url value audit path) (drop-type path))
                   :else (fail)))]
         {:payload (project policy payload [])
          :audit (assoc @audit :review-required true
                        :limitations [:heuristic-text-detection :explicit-copy-can-retain-contacts
                                      :url-paths-retained :no-anonymization-guarantee])})))))
