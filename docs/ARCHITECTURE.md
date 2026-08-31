# Architecture

```text
Client -> Ring API -> payment application -> Datomic
                           |                 |
                           v                 v
                    routing policy      audit / ledger / relay
                           |
                           v
                    PaymentGateway catalog
                           |
                     Fake or Stripe adapter
```

The public API uses canonical payment fields; provider code is confined to `provider/`. Datomic repositories own persistence queries. Idempotency protects client writes, webhook-event identity protects provider delivery, and the Datomic log relay publishes at-least-once events to Kafka. The ledger is double-entry and audit queries use Datomic history/as-of.

The routing policy is a pure function. It chooses merchant, currency, payment-method, default, or lowest-cost policy routes only from compatible available provider descriptors. A configured provider being unavailable is an error, never a silent financial fallback. Security boundaries and known limits are in `SECURITY.md`; operational actions are in `docs/runbooks/`; measured local behavior is in `docs/PERFORMANCE.md`.

Pix is represented as a canonical payment action attached to the payment: `:payment-action/type`, `:payment-action/payload`, optional `:payment-action/qr-code-url`, optional `:payment-action/hosted-instructions-url`, and `:payment-action/expires-at`. The Stripe mapper translates only those Pix QR-code fields into this action. It deliberately never persists or returns the full provider `next_action` payload.

Boleto uses the same action component with type `:boleto/voucher`: the payload is the voucher number, the hosted-instructions URL is the hosted voucher, the optional document URL is its PDF, and the expiry remains canonical. Generating a voucher keeps the payment in `requires-action`; only a verified asynchronous provider webhook settles or fails it.

Subscriptions, invoices, and payments are distinct Datomic entities. A subscription describes the recurring agreement, an invoice records a single receivable, and an invoice may reference the payment used to collect it. This preserves the payment boundary and prevents recurring-provider concepts from leaking into the payment aggregate.
