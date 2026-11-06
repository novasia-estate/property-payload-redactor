# Property Payload Redactor

A dependency-free Clojure library and EDN command-line tool for preparing a deliberately small draft of a property payload before sharing it with a designer, translator or demo environment.

Developed by [NovAsia Estate](https://novasia.estate/). MIT. Every bundled contact, URL and property is synthetic.

## The practical problem

A property payload combines public listing fields, image URLs with tracking parameters and internal notes. A recursive "remove a few private keys" rule can leave unexpected new fields behind. This tool projects only an authored policy tree, cleans selected URLs, applies limited text redaction and creates an audit that never copies unknown field names or values.

It is **not an anonymization guarantee**. Every result requires human review. A `:copy-scalar` leaf deliberately preserves scalar text, including any contacts it contains. Text detection is limited; URL paths remain intact. Read the [limits](docs/limits.md) before using real data.

## Install

```clojure
;; deps.edn, using Clojars as a repository
{:deps {net.clojars.novasia-estate/property-payload-redactor {:mvn/version "1.0.0"}}}
```

Requires Clojure 1.12.6. Java 17 and 21 are checked. No runtime dependencies beyond Clojure and its normal runtime dependencies.

## Use

```clojure
(require '[novasia.payload-redactor :as redact])

(redact/redact-payload
 {:id :copy-scalar :description :redact-text :url :clean-url}
 {:id "synthetic-42"
  :description "Synthetic contact demo@example.com"
  :url "https://demo.invalid/unit%2F42?tracking=example"
  :private-notes "synthetic excluded value"})
;; :payload => {:id "synthetic-42"
;;              :description "Synthetic contact [email]"
;;              :url "https://demo.invalid/unit%2F42"}
;; :audit contains known policy paths, fixed actions and counts.
```

```sh
clojure -M -m novasia.payload-redactor.cli examples/policy.edn examples/payload.edn payload.edn audit.edn
clojure -M:test
```

An optional fifth CLI argument is an options EDN file such as `{:literals ["Synthetic Agent Name"]}`. Those literals are case-sensitive substrings, processed longest first. Output files must be new and distinct. Exit 0 means draft files were produced; it never certifies the payload is safe. Exit 2 means validation or file failure. An interrupted pair of writes can leave partial output files; inspect both before sharing.

- [Projection contract](docs/input.md)
- [Text and URL limits](docs/limits.md)
- [Integration and review](docs/integration.md)
- [Behavioral tests](test/novasia/payload_redactor/core_test.clj)

No network requests, credentials, telemetry, automatic publishing or background storage. Run locally, review the actual output, and share only what you intend to disclose.
