# Architecture

```text
Client -> Ring API -> payment application -> Datomic
                           |                 |
                           v                 v
                    PaymentGateway       audit / ledger / relay
                           |
                     Fake or Stripe adapter
```

The public API uses canonical payment fields; provider code is confined to `provider/`. Datomic repositories own persistence queries. Idempotency protects client writes, webhook-event identity protects provider delivery, and the Datomic log relay publishes at-least-once events to Kafka. The ledger is double-entry and audit queries use Datomic history/as-of.

Security boundaries and known limits are in `SECURITY.md`; operational actions are in `docs/runbooks/`; measured local behavior is in `docs/PERFORMANCE.md`.
