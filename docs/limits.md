# Text and URL limits

Text matching intentionally covers only an ASCII email-like syntax and plus-prefixed phone-like strings with 7–15 digits. It can miss obfuscated addresses, internationalized email, local phone formats, extensions and unusual punctuation. It can also flag text that is not a real contact. These are review aids, not a complete PII detector.

Caller-supplied literal substrings are case-sensitive and matched without word boundaries. Longer literals run first. A short literal can redact part of a longer unrelated word. Source spelling and Unicode are otherwise preserved. No names, addresses, passports, identity data or faces are inferred. Include suitable literals yourself and inspect the result.

URL cleanup does not fetch, validate the ownership of, or resolve a host. Removing a query does not make a URL public or anonymous. Private document IDs, client names and tokens in URL paths remain. The raw path preserves encoded separators such as %2F; the cleaner does not decode or re-encode it.

The output always has `:review-required true` and fixed limitation markers. This flag remains true even when the audit reports no changes. No result is approved for publication automatically.
