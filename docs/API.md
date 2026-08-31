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

Create request (Boleto):

```json
{"customerId":"cust-demo","amount":1000,"currency":"BRL","method":"boleto","boleto":{"taxId":"000.000.000-00","email":"succeed_immediately@example.com","name":"Boleto Test","address":{"line1":"1234 Av Paulista","city":"Sao Paulo","state":"SP","postalCode":"01310-000","country":"BR"}}}
```

A Pix create response in `requires-action` contains only the canonical action:

```json
{"id":"...","status":"requires-action","amount":12990,"currency":"BRL","action":{"type":"pix_qr_code","payload":"000201...","qrCodeUrl":"https://...","hostedInstructionsUrl":"https://...","expiresAt":"2030-01-01T00:00:00Z"}}
```

`payload` is the copy-and-paste Pix value. `qrCodeUrl` and `hostedInstructionsUrl` are present only when supplied by the provider. The API never exposes Stripe's full `next_action` object or raw provider payloads.

The `pix` object is used only to create the provider payment method; it is never persisted or logged. In Stripe test mode, `000.000.000-00` is the documented test CPF. Production callers must provide the actual required customer details through an approved privacy process.

A Boleto create response remains `requires-action`; voucher generation is not payment confirmation:

```json
{"id":"...","status":"requires-action","amount":1000,"currency":"BRL","action":{"type":"boleto_voucher","number":"001905...","hostedVoucherUrl":"https://...","pdfUrl":"https://...","expiresAt":"2030-01-03T23:59:59Z"}}
```

The `boleto` object is used only for provider confirmation and is never persisted or logged. Stripe delivers `payment_intent.succeeded` after payment and `payment_intent.payment_failed` after expiry; the signed webhook is the source of those status changes. Stripe documents that Boleto payments cannot be refunded.

## Refund a payment

`POST /v1/payments/{paymentId}/refunds` creates one immutable refund. `amount`
is an integer in BRL minor units. The service accepts partial and multiple
refunds, but rejects a request whose total would exceed the captured amount.

```json
{"amount": 400}
```

Successful requests return `201` with the canonical refund and resulting
payment status. The API returns `409 refund_amount_exceeds_captured` when the
aggregate refund amount would exceed the captured payment amount.

Send `Idempotency-Key` with every create. A repeated equivalent request returns the original payment; a changed request under the same key returns `409`.

Provider selection is internal. Routing can use merchant, currency, payment method, a configured default, availability, or configured cost; no provider name belongs in this public request. A route with no safe available provider fails rather than charging through an alternative provider.
