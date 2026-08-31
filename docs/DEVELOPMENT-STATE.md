# Development state

- Milestone: M20 — Boleto
- Status: COMPLETED
- Completed: Canonical Boleto payment method and voucher action; Stripe direct PaymentIntent confirmation with required billing data; Fake Provider, API, Datomic schema/persistence, mapper fixture, and sandbox integration coverage; documented asynchronous webhook semantics and non-refundable provider limitation.
- Last tests: Unit suite passed (68 tests, 196 assertions); integration suite passed (20 tests, 67 assertions); Stripe Boleto sandbox passed (1 test, 6 assertions) and Stripe card sandbox passed (1 test, 3 assertions) using `.env`. The first Boleto live request correctly identified the provider minimum amount (`amount_too_small` at 100), then passed at 1000. M19 Pix remains externally blocked by Stripe account eligibility (request ID `req_2I5Qqs4IRiNkdE`) and was not treated as an M20 blocker.
- Decision: A generated Boleto voucher is `requires-action`, never paid. Only canonical voucher data is persisted; customer billing details are transient and never logged. Stripe's documented Boleto refund limitation is retained rather than inventing a refund flow.
- Next action: Await explicit authorization for M21. Do not start it automatically.
