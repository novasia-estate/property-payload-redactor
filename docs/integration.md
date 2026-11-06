# Integration and review

`read-safe` reads bounded EDN. `redact-payload` accepts a validated policy tree, a map payload and optional `{:literals [...]}`. All values remain immutable. No output is written by the library itself.

Audit events contain only known policy paths (keywords and zero-based vector indices), fixed action names and counts. Unknown dropped keys are counted at their parent path; neither their names nor their values are included. Source values never enter error messages. A policy itself can still contain revealing field names or literals, so keep it under your control.

The event list stops at 10,000 entries. Aggregate counts continue, and `:omitted-event-count` reports the omitted event count. This makes truncation explicit; it does not turn findings into a success state.

Before sharing: inspect actual payload values and retained URL paths, check explicit-copy leaves, review redactions for missed contacts and false positives, and compare counts with the intended projection. A holder of the source can link output records and audit paths back to people. This is projection and limited cleanup, not an anonymization certificate.

The CLI creates two new files exclusively. Existing inputs/outputs are never overwritten. A write failure can leave partial files, so inspect the pair. Standard output contains a fixed completion message; errors contain a fixed failure message. The library and command have no network or publishing integration.
