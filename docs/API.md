# API v1

All `/v1` endpoints require `Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>`. Amounts are integer minor units; raw card data is not accepted. `X-Merchant-Id` scopes reads and payment creation; omitting it uses the backwards-compatible `default` merchant.

| Method | Route | Purpose |
| --- | --- | --- |
| POST | `/v1/payments` | Create an idempotent payment |
| GET | `/v1/payments/:id` | Read current payment; optional `asOf` ISO instant or Datomic t |
| GET | `/v1/payments/:id/history` | Temporal status history |
| GET | `/v1/payments/:id/ledger` | Balanced ledger journals |
| POST | `/webhooks/stripe` | Stripe-signed webhook; no API key |
| GET | `/metrics` | Authenticated plain-text metrics |

Create request (card):

```json
{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"card"}
```

Create request (Pix):

```json
{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"pix","pix":{"taxId":"000.000.000-00","email":"pix@example.test","name":"Pix Test"}}
```

A Pix create response in `requires-action` contains only the canonical action:

```json
{"id":"...","status":"requires-action","amount":12990,"currency":"BRL","action":{"type":"pix_qr_code","payload":"000201...","qrCodeUrl":"https://...","hostedInstructionsUrl":"https://...","expiresAt":"2030-01-01T00:00:00Z"}}
```

`payload` is the copy-and-paste Pix value. `qrCodeUrl` and `hostedInstructionsUrl` are present only when supplied by the provider. The API never exposes Stripe's full `next_action` object or raw provider payloads.

The `pix` object is used only to create the provider payment method; it is never persisted or logged. In Stripe test mode, `000.000.000-00` is the documented test CPF. Production callers must provide the actual required customer details through an approved privacy process.

Send `Idempotency-Key` with every create. A repeated equivalent request returns the original payment; a changed request under the same key returns `409`.

Provider selection is internal. Routing can use merchant, currency, payment method, a configured default, availability, or configured cost; no provider name belongs in this public request. A route with no safe available provider fails rather than charging through an alternative provider.
