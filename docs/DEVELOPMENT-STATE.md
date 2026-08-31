# Development state

- Milestone: M21 — Subscriptions
- Status: COMPLETED
- Completed: Independent canonical Subscription and Invoice aggregates; Datomic schema and repository; invoice-to-payment reference; unit and integration coverage; architecture, README, and ADR documentation.
- Last tests: Unit suite passed (70 tests, 201 assertions); integration suite passed (21 tests, 69 assertions).
- Decision: A subscription is an agreement and an invoice is an individual receivable. Payment remains a separate consequence of collection, rather than becoming a recurring aggregate.
- Next action: Await explicit authorization for M22. Do not start it automatically.
