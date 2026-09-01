# Development state

- Milestone: M24 — Consumer webhook delivery
- Status: IN_PROGRESS
- Completed: Isolated Dispute aggregate, state machine, Datomic schema/repository, provider-reference deduplication, and unit/integration coverage. Payment remains unchanged by dispute lifecycle changes.
- Last tests: Unit and integration suites run for M23.
- Decision: Disputes/chargebacks are a bounded context linked to payment IDs; they do not become Payment statuses.
- Next action: Add integration coverage for the Datomic delivery repository and wire emitted payment events to configured consumer endpoints; then run all suites and document endpoint configuration. Durable delivery logs and dead-letter persistence are now implemented but not yet integration-validated.
