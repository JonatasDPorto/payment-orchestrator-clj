# Observability demo

Start the local stack with `docker compose up --build`. Prometheus is available at
`http://localhost:9090` and Grafana at `http://localhost:3000` (default local
credentials are `admin` / `admin`). The provisioned **Payment Orchestrator
Operations** dashboard uses the application `/metrics` endpoint.

Create a payment with an `Idempotency-Key`, `X-Request-Id`, and optional
`X-Correlation-Id`. The response contains the payment id and echoes the request
id. Search structured application logs by either id. A `traceparent` header is
accepted to continue an upstream trace; otherwise a root trace is created.

Provider and webhook work are represented as `provider.create`,
`webhook.receive`, `webhook.persist`, and `payment.apply-provider-event` spans.
The runtime tracer is intentionally no-op until an exporter is injected at the
composition boundary, so a collector outage cannot affect payments. Tests inject
the in-memory collector only as a test double.

Use a provider webhook against `/webhooks/stripe` or `/webhooks/asaas` after a
payment is created. The dashboard exposes request traffic, creates, provider
errors/latency, webhook receipt/duplicates/lag, transitions, reconciliation,
relay lag, and ledger invariant failures when their existing metric series are
emitted.
