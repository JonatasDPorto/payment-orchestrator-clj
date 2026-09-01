# Development state

- Milestone: M23 — Disputes/Chargebacks
- Status: COMPLETED
- Completed: Isolated Dispute aggregate, state machine, Datomic schema/repository, provider-reference deduplication, and unit/integration coverage. Payment remains unchanged by dispute lifecycle changes.
- Last tests: Unit and integration suites run for M23.
- Decision: Disputes/chargebacks are a bounded context linked to payment IDs; they do not become Payment statuses.
- Next action: Await explicit authorization for M24. Do not start it automatically.
