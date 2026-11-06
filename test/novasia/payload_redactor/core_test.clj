(ns novasia.payload-redactor.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [novasia.payload-redactor :as r]))

(deftest projection-excludes-unknown
  (let [x (r/redact-payload {:id :copy-scalar} {:id "A" :secret "PRIVATE"})]
    (is (= {:id "A"} (:payload x)))
    (is (= 1 (get-in x [:audit :counts :unknown-fields-dropped])))
    (is (not (str/includes? (pr-str (:audit x)) "secret")))))
(deftest nested-projection
  (is (= {:location {:district "D"}}
         (:payload (r/redact-payload {:location {:district :copy-scalar}}
                                     {:location {:district "D" :owner "PRIVATE"}})))))
(deftest scalar-does-not-copy-map
  (is (= {} (:payload (r/redact-payload {:data :copy-scalar} {:data {:secret "X"}})))))
(deftest scalar-does-not-copy-vector
  (is (= {} (:payload (r/redact-payload {:data :copy-scalar} {:data ["PRIVATE"]})))))
(deftest copy-false-nil-zero
  (is (= {:a false :b nil :c 0} (:payload (r/redact-payload {:a :copy-scalar :b :copy-scalar :c :copy-scalar} {:a false :b nil :c 0})))))
(deftest explicit-copy-retains-text
  (is (= {:id "agent@example.com"} (:payload (r/redact-payload {:id :copy-scalar} {:id "agent@example.com"}))))
  (is (true? (get-in (r/redact-payload {:id :copy-scalar} {:id "A"}) [:audit :review-required]))))
(deftest simple-emails-redacted
  (is (= {:text "Ask [email] or [email]."}
         (:payload (r/redact-payload {:text :redact-text} {:text "Ask a@example.com or b+tag@example.org."})))))
(deftest plus-phone-redacted
  (is (= {:text "Call [phone] today"}
         (:payload (r/redact-payload {:text :redact-text} {:text "Call +855 12 345 678 today"})))))
(deftest local-phone-not-claimed-detected
  (is (= {:text "012345678"} (:payload (r/redact-payload {:text :redact-text} {:text "012345678"})))))
(deftest explicit-literals-longest-first
  (is (= {:text "[redacted] and [redacted]"}
         (:payload (r/redact-payload {:text :redact-text} {:text "Anna and Ann"} {:literals ["Ann" "Anna"]})))))
(deftest unicode-literal
  (is (= {:text "บ้าน [redacted]"}
         (:payload (r/redact-payload {:text :redact-text} {:text "บ้าน Сергей"} {:literals ["Сергей"]})))))
(deftest literal-regex-metacharacters
  (is (= {:text "[redacted]"} (:payload (r/redact-payload {:text :redact-text} {:text "A+B"} {:literals ["A+B"]})))))
(deftest value-free-redaction-audit
  (let [a (:audit (r/redact-payload {:text :redact-text} {:text "demo@example.com +85512345678"}))]
    (is (= 1 (get-in a [:counts :email-redacted])))
    (is (= 1 (get-in a [:counts :phone-redacted])))
    (is (not (str/includes? (pr-str a) "demo@example")))
    (is (not (str/includes? (pr-str a) "855")))))
(deftest url-components-stripped
  (is (= {:url "https://example.com/a%2Fb"}
         (:payload (r/redact-payload {:url :clean-url} {:url "https://name:password@EXAMPLE.com/a%2Fb?secret=x#private"})))))
(deftest url-preserves-port
  (is (= {:url "https://example.com:8443/path"}
         (:payload (r/redact-payload {:url :clean-url} {:url "https://example.com:8443/path?a=1"})))))
(deftest url-private-path-is-not-anonymized
  (is (= {:url "https://example.com/private-name"}
         (:payload (r/redact-payload {:url :clean-url} {:url "https://example.com/private-name"})))))
(deftest url-http-rejected
  (is (= {} (:payload (r/redact-payload {:url :clean-url} {:url "http://example.com/"})))))
(deftest url-malformed-rejected
  (doseq [url ["not a URL" "https://ex ample.com" "https://example.com:70000/x" "https://例子.com/"]]
    (is (= {} (:payload (r/redact-payload {:url :clean-url} {:url url}))))))
(deftest urls-never-fetched
  (is (= {:url "https://never-resolves.invalid/path"}
         (:payload (r/redact-payload {:url :clean-url} {:url "https://never-resolves.invalid/path?x"})))))
(deftest vector-order-preserved
  (is (= {:photos [{:title "A"} {:title "B"}]}
         (:payload (r/redact-payload {:photos [{:title :redact-text}]}
                                     {:photos [{:title "A" :secret "X"} {:title "B"}]})))))
(deftest wrong-array-element-is-nil
  (let [result (r/redact-payload {:photos [{:title :redact-text}]} {:photos ["PRIVATE" {:title "B"}]})]
    (is (= {:photos [nil {:title "B"}]} (:payload result)))
    (is (= [:photos 0] (get-in result [:audit :events 0 :path])))))
(deftest wrong-field-type-is-omitted
  (is (= {} (:payload (r/redact-payload {:text :redact-text} {:text 42}))))
  (is (= {} (:payload (r/redact-payload {:items [:copy-scalar]} {:items '(1 2)})))))
(deftest missing-field-known-path
  (is (= {:path [:title] :action :missing-field :count 1}
         (get-in (r/redact-payload {:title :redact-text} {}) [:audit :events 0]))))
(deftest empty-literal-rejected
  (is (thrown? Exception (r/redact-payload {:title :redact-text} {} {:literals [""]}))))
(deftest bad-policy-leaf-rejected
  (is (thrown? Exception (r/redact-payload {:title :copy-everything} {}))))
(deftest arbitrary-map-key-rejected
  (is (thrown? Exception (r/redact-payload {"title" :copy-scalar} {}))))
(deftest qualified-key-rejected
  (is (thrown? Exception (r/redact-payload {:private/title :copy-scalar} {}))))
(deftest bad-array-policy-rejected
  (is (thrown? Exception (r/redact-payload {:items [:copy-scalar :copy-scalar]} {}))))
(deftest unknown-options-rejected
  (is (thrown? Exception (r/redact-payload {:id :copy-scalar} {} {:implicit-copy true}))))
(deftest unsafe-tags-rejected
  (doseq [raw ["#inst \"2026-11-15\"" "#uuid \"00000000-0000-0000-0000-000000000000\"" "#foo \"PRIVATE\"" "#=(System/exit 1)"]]
    (is (thrown? Exception (r/read-safe raw)))))
(deftest exactly-one-edn-value
  (is (= {:id "A"} (r/read-safe "{:id \"A\"} ; trailing comment\n")))
  (is (thrown? Exception (r/read-safe "{} {}")))
  (is (thrown? Exception (r/read-safe ""))))
(deftest nonfinite-input-rejected
  (is (thrown? Exception (r/redact-payload {:x :copy-scalar} {:x Double/NaN}))))
(deftest depth-limit
  (is (thrown? Exception (r/read-safe (str (apply str (repeat 15 "[")) "1" (apply str (repeat 15 "]")))))))
(deftest collection-limit
  (is (thrown? Exception (r/redact-payload {:items [:copy-scalar]} {:items (vec (range 10001))}))))
(deftest audit-cap-still-counts-all-drops
  (let [x (r/redact-payload {:items [{:title :copy-scalar}]} {:items (vec (repeat 6000 {:extra "X"}))})]
    (is (= 10000 (count (get-in x [:audit :events]))))
    (is (= 2000 (get-in x [:audit :omitted-event-count])))
    (is (= 6000 (get-in x [:audit :counts :unknown-fields-dropped])))))
(deftest immutable-independent-results
  (let [payload {:title "a@example.com" :private "X"} before payload
        x (r/redact-payload {:title :redact-text} payload)]
    (is (= before payload))
    (is (= x (r/redact-payload {:title :redact-text} payload)))))
(deftest example-protocol
  (let [policy (r/read-safe (slurp "examples/policy.edn"))
        input (r/read-safe (slurp "examples/payload.edn"))
        x (r/redact-payload policy input)]
    (is (= "synthetic-42" (get-in x [:payload :id])))
    (is (= "https://demo.invalid/listings/unit%2F42" (get-in x [:payload :url])))
    (is (not (str/includes? (pr-str x) "SYNTHETIC PRIVATE")))
    (is (= 2 (get-in x [:audit :counts :email-redacted])))))
