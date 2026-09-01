# Demo script and recording checklist

1. `Copy-Item .env.example .env`; set a private `PAYMENT_ORCHESTRATOR_API_KEY` (Compose has a local-only fallback solely to keep a fresh clone runnable).
2. `docker compose up --build`.
3. Configure `PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER=stripe` and create a payment with the canonical curl command in the README.
4. Change only `PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER=asaas`, restart, and repeat exactly the same canonical request; the response format remains identical.
5. GET the payment, history, and ledger routes using the returned id.
6. Run `docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration` and point out webhook duplicate, relay-restart, reconciliation, temporal audit, and ledger scenarios.
7. Run the performance command from `docs/PERFORMANCE.md`.

Recording checklist: hide `.env`; use sandbox-only provider values; show commands and outputs at readable size; stop containers with `docker compose down` after recording.
