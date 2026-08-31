# Development state

- Milestone: M22 — Refunds avançados
- Status: COMPLETED
- Completed: Canonical immutable refunds with partial and multiple refund flows; aggregate refund invariant; HTTP API; Datomic persistence; provider amount mapping; reconciliation snapshots; property, concurrency, API, and integration coverage.
- Last tests: Unit suite passed (77 tests, 218 assertions); integration suite passed (22 tests, 71 assertions).
- Decision: Refunds remain immutable records linked to a payment; reconciliation records mismatches but never changes financial history automatically.
- Next action: Await explicit authorization for M23. Do not start it automatically.
