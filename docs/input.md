# Projection contract

The policy is a nonempty EDN map. Keys are unqualified keywords with 1–64 lowercase ASCII letters, digits, underscores or hyphens, starting with a letter. Values are one of three leaf keywords, nested maps, or a one-element vector describing an array element.

`{:photos [{:url :clean-url :caption :redact-text}]}` keeps only those two fields from each photo object. Unknown fields disappear. A wrong-type map field is omitted. A wrong-type array element becomes nil to preserve array length and indices. Known missing fields are omitted and recorded. Array input must be a vector, not a list.

`:copy-scalar` accepts nil, booleans, finite numbers and strings. It rejects maps, arrays, symbols, keywords and characters. This leaf is an explicit copy permission, including any contact data contained in a string. Choose it only for fields you have reviewed.

`:redact-text` accepts strings and applies the limited heuristics/literals documented separately. `:clean-url` accepts parseable absolute HTTPS URLs with ASCII DNS/IPv4 hosts and a valid port; it strips user information, query and fragment, lowercases the host, and preserves the raw encoded path. IPv6 and Unicode hostname forms are rejected; use an explicit ASCII/punycode hostname upstream if appropriate.

Limits: policy depth 12 and 200 nodes; input/options depth 12, at most 100,000 total nodes, 10,000 entries per collection, 100,000 characters per string and 2,000,000 total string characters. The safe reader and CLI accept at most 2 MB UTF-8 per input. Invalid Unicode and nonfinite numbers are rejected. Literal options accept at most 20 nonempty strings of up to 128 characters.

The safe EDN reader accepts exactly one value, allows trailing comments, and rejects custom tags, #inst, #uuid and reader evaluation. Invalid batches fail with a fixed message. Do not call general Clojure read-string on untrusted input.
