# Demo script and recording checklist

1. `Copy-Item .env.example .env`; set a private `PAYMENT_ORCHESTRATOR_API_KEY` (Compose has a local-only fallback solely to keep a fresh clone runnable).
2. `docker compose up --build`.
3. Create a Fake Provider payment with the curl command in the README; repeat it with the same idempotency key and show one payment.
4. GET the payment, history, and ledger routes using the returned id.
5. Run `docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration` and point out webhook duplicate, relay-restart, reconciliation, temporal audit, and ledger scenarios.
6. Run the performance command from `docs/PERFORMANCE.md`.

Recording checklist: hide `.env`; use test-only Stripe values; show commands and outputs at readable size; do not claim Asaas support (M8 was skipped); stop containers with `docker compose down` after recording.
