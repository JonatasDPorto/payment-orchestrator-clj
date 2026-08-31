# API v1

All `/v1` endpoints require `Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>`. Amounts are integer minor units; raw card data is not accepted.

| Method | Route | Purpose |
| --- | --- | --- |
| POST | `/v1/payments` | Create an idempotent payment |
| GET | `/v1/payments/:id` | Read current payment; optional `asOf` ISO instant or Datomic t |
| GET | `/v1/payments/:id/history` | Temporal status history |
| GET | `/v1/payments/:id/ledger` | Balanced ledger journals |
| POST | `/webhooks/stripe` | Stripe-signed webhook; no API key |
| GET | `/metrics` | Authenticated plain-text metrics |

Create request:

```json
{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"card"}
```

Send `Idempotency-Key` with every create. A repeated equivalent request returns the original payment; a changed request under the same key returns `409`.
