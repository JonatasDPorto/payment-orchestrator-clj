# Technical Milestones & Reference Guide

> This document details the step-by-step technical milestone implementation history (M1 to M24), REPL commands, contract testing, and local integration instructions for Payment Orchestrator in Clojure.

Provider-agnostic payment orchestration API built with Clojure and Datomic. It demonstrates safe payment boundaries: idempotency, provider isolation, durable webhook processing, temporal audit, double-entry accounting, reconciliation, Kafka relay, observability, and security hardening.

> Status: v1.0.0 release candidate. Stripe sandbox is supported; the Asaas milestone was intentionally skipped.

## Quick start with Docker

```powershell
Copy-Item .env.example .env
# Set PAYMENT_ORCHESTRATOR_API_KEY in .env to a private local value.
docker compose up --build
```

Create a payment in a second terminal, replacing the placeholder with the `.env` value:

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "X-Merchant-Id: demo-merchant" -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"card"}'
```

See [API.md](API.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DEMO.md](DEMO.md), [PERFORMANCE.md](PERFORMANCE.md), and [SECURITY.md](../SECURITY.md).

Payment Orchestrator in Clojure is an open-source provider-agnostic payment orchestration platform built with Clojure and Datomic. The HTTP API, Datomic Local, and the M5 Fake Provider are available for local validation.

## Prerequisites

- Java 21 or newer
- [Clojure CLI](https://clojure.org/guides/install_clojure)

## Running locally

```bash
clojure -M -m payment-orchestrator-clj.core
```

The bootstrap registers the service and starts Jetty on port 8080.

## Tests

```bash
clojure -M:test
```

Without installing the Clojure CLI on the host, run the same suite with Docker:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test
```

Datomic integration tests are separate:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration
```

## Local logging

The project uses `slf4j-simple` with timestamp, thread, logger, and `INFO` level. The bootstrap logs only the service name and environment; it does not log payloads, customer identifiers, tokens, or secrets. Configuration is in `resources/simplelogger.properties`.

## HTTP API (M3)

Start the API at `http://localhost:8080`:

```powershell
docker run --rm -p 8080:8080 --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M -m payment-orchestrator-clj.core
```

In another terminal, set `PAYMENT_ORCHESTRATOR_API_KEY` in `.env` and send it as a Bearer token:

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-123","amount":12990,"currency":"BRL","method":"card"}'
```

## API Idempotency (M4)

`POST /v1/payments` requires an idempotency key. Repeating the same key with the same payload returns the original payment; a different payload returns `409`.

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-123","amount":12990,"currency":"BRL","method":"card"}'
```

## Payment Gateway Port (M5)

The gateway is defined by the `PaymentGateway` protocol; the default environment uses the deterministic Fake Provider (`:always-success`). POST creates the local payment and returns `processing`, persisting the canonical provider reference. `:always-fail`, `:timeout`, and `:requires-action` modes are covered by contract tests. No host installation is required.

Validate contract, flow, and persistence exclusively via Docker:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration
```

## Stripe Sandbox Adapter (M6)

Stripe is optional and remains isolated in `provider/stripe/`. To run the sandbox test, use a test key and a test payment method only in the local environment; never commit these values to versioned files.

Create your local file from the example:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and provide your sandbox `STRIPE_SECRET_KEY`. The `.env` file is ignored by Git; `.env.example` contains no real credentials and is versioned.

```powershell
docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-stripe-sandbox
```

To start the API with Stripe, set the same variables and select the provider by environment (the default remains the Fake Provider):

```powershell
docker run --rm -p 8080:8080 --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M -m payment-orchestrator-clj.core
```

## Stripe Webhook Inbox (M7)

`POST /webhooks/stripe` validates the `Stripe-Signature` header against the raw body before parsing JSON. Valid events enter an idempotent Datomic inbox and are processed asynchronously out-of-request. Only SHA-256 hashes and operational fields are persisted; full payloads are not stored.

For the local demonstration, include `STRIPE_WEBHOOK_SECRET` in `.env`, start the API using the previous command, and forward events via the Stripe CLI:

```powershell
stripe listen --forward-to http://localhost:8080/webhooks/stripe
```

Copy the `whsec_...` secret displayed by the CLI to `STRIPE_WEBHOOK_SECRET` and restart the container. Next, create a payment via the M6 API: the Payment Intent created by the application will be forwarded by the CLI and associated with the local payment.

To validate only endpoint delivery with an independent event, use:

```powershell
stripe trigger payment_intent.succeeded
```

## Provider routing (M18)

Provider selection remains internal to the API. The pure routing policy supports default, merchant, currency, payment-method, availability, and lowest-cost choices from the configured gateway catalog. The public request never exposes provider-specific fields. A route whose chosen provider is unavailable fails safely; it is never retried through another provider, preventing an ambiguous operation from creating a duplicate charge. See [MULTI-TENANCY.md](MULTI-TENANCY.md) for the local configuration contract.

## Pix (M19)

Pix is the first canonical payment capability that returns a customer action. The local Fake Provider and Stripe Pix adapter return a provider-neutral copy-and-paste payload, QR-code URL when supplied, hosted instructions URL when supplied, and expiry, with no raw Stripe object persisted. The transient Pix customer details required to create the provider payment method are never persisted or logged.

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "X-Merchant-Id: demo-merchant" -H "Content-Type: application/json" -H "Idempotency-Key: pix-demo-001" -d '{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"pix","pix":{"taxId":"000.000.000-00","email":"succeed_immediately@example.com","name":"Pix Test"}}'
```

To validate the real Stripe sandbox adapter with a `sk_test_...` key, run:

```powershell
docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-stripe-pix-sandbox
```

The sandbox suite sends `payment_method_types[]=pix`, `currency=brl`, `confirm=true`, and the documented test CPF. It never claims success when Stripe reports that the account is ineligible; inspect the returned request ID in that case.

## Boleto (M20)

Boleto is a provider-neutral voucher action. Its generated number, hosted voucher URL, optional PDF URL, and expiry are persisted without retaining the billing details used to create it. Generating the voucher returns `requires-action`; settlement or expiry is applied only by the signed Stripe webhook.

```powershell
docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-stripe-boleto-sandbox
```

Stripe requires BRL and a minimum amount for Boleto; the sandbox suite uses `1000` minor units. Boleto payments cannot be refunded through Stripe.

## Subscriptions (M21)

Subscriptions, invoices, and payments are separate aggregates. A subscription stores the recurring agreement; an invoice records a single amount due and can reference the payment created to collect it. This milestone intentionally does not call a provider recurring-billing API or schedule collections automatically.

## Advanced refunds (M22)

Refunds are immutable canonical records. A payment may receive partial or
multiple refunds while their sum remains at or below the captured amount. The
payment becomes `partially-refunded` or `refunded` from that aggregate. Provider
refunds receive only the original provider payment reference and an amount; no
provider-specific object is returned by the public API.

```powershell
curl.exe -X POST "http://localhost:8080/v1/payments/<PAYMENT_ID>/refunds" -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "Content-Type: application/json" -d '{"amount":400}'
```

The response is `409 refund_amount_exceeds_captured` when the requested amount
would make the aggregate exceed the captured amount. Refund reconciliation
records provider/local amount mismatches for investigation without changing
financial history automatically.

## Disputes and chargebacks (M23)

Disputes are a separate bounded context linked to a payment ID, rather than a
new Payment status. Their provider reference is unique and their lifecycle is
tracked independently as `needs-response`, `under-review`, `won`, or `lost`.

## Consumer webhooks (M24)

Set `PAYMENT_ORCHESTRATOR_WEBHOOK_ENDPOINTS` to comma-separated HTTPS endpoints and
`PAYMENT_ORCHESTRATOR_WEBHOOK_SECRET` to the shared HMAC-SHA256 secret. Payment
events are persisted before delivery. POST requests carry JSON plus
`X-Payment-Orchestrator-Event-Id` and `X-Payment-Orchestrator-Signature`.
Non-2xx responses and transport failures retry up to five attempts, then become
dead-letter records; endpoint/event pairs are deduplicated. Run delivery with:

```powershell
docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M -m payment-orchestrator-clj.consumer-webhook.runner
```

## Development REPL

```bash
clojure -M:dev
```

## Payment domain (M1)

The domain is independent of database, HTTP, and providers. Monetary amounts use the smallest integer unit: `12990` represents 129.90 BRL.

In the REPL:

```clojure
(require '[payment-orchestrator-clj.payment.domain :as payment])

(def p (payment/new-payment {:id #uuid "2ee9a79d-8ccf-4c75-89a2-beb89b271ca1"
                             :customer-id "customer-123"
                             :amount 12990
                             :currency :BRL
                             :method :payment.method/card}))
(payment/transition p :payment.status/processing)
```

## Datomic persistence (M2)

The Datomic repository accepts and returns domain maps; the Client API is confined to infrastructure. In tests, Datomic Local uses isolated in-memory databases. The initial schema and its first version are located in `src/payment_orchestrator_clj/datomic/schema/`.

Run the separate integration suite:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration
```

## Documentation

- [Architecture](ARCHITECTURE.md)
- [API](API.md)
- [Demo and recording checklist](DEMO.md)
- [Performance profile](PERFORMANCE.md)
- [Security policy](../SECURITY.md)
- [Contributing](../CONTRIBUTING.md)
- [v1.0.0 release notes](RELEASE-NOTES-v1.0.0.md)
