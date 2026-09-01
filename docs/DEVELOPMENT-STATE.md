# Development state

- Milestone: M8 — Asaas Adapter
- Status: IN_PROGRESS
- Completed: Isolated Dispute aggregate, state machine, Datomic schema/repository, provider-reference deduplication, and unit/integration coverage. Payment remains unchanged by dispute lifecycle changes.
- Last tests: Unit and integration suites run for M23.
- Decision: Disputes/chargebacks are a bounded context linked to payment IDs; they do not become Payment statuses.
- Next action: Inspect the current Stripe provider implementation and provider contract suite, then add the Asaas adapter in isolation. M8 was skipped originally and is now authorized retroactively; preserve all later milestones.
