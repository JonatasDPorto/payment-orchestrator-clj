# Demo script and recording checklist

1. `Copy-Item .env.example .env`; set a private `PAYMENT_ORCHESTRATOR_API_KEY`. For an offline demo, set `PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER=fake`.
2. `docker compose up --build`.
3. Set `PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER=stripe` with sandbox credentials and create a payment with the canonical curl command in the README.
4. Change only `PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER=asaas`, restart, and repeat the same request. The request and response contract remain canonical; provider credentials stay in `.env`.
5. Repeat a request with the same `Idempotency-Key`, then with the same key and a changed amount, to show replay and conflict behavior.
6. GET the returned payment, `/history`, and `/ledger`; use `asOf` on the payment history flow to inspect temporal state. Ledger postings are balanced by construction.
7. Run `docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration` for reproducible webhook duplicate, relay/Kafka consumer, reconciliation/unknown-outcome, and temporal-audit demonstrations.
8. Open Grafana at `http://localhost:3000`; see `OBSERVABILITY.md` for correlation/payment lookup and provider spans.

Recording checklist: hide `.env`; use sandbox-only provider values; show commands and outputs at readable size; stop containers with `docker compose down` after recording.
