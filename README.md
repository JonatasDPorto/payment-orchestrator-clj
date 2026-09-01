# Payment Orchestrator in Clojure

A provider-agnostic, high-performance payment orchestration engine built with **Clojure** and **Datomic**.

Payment Orchestrator provides a safe, idempotent, and unified API boundary across multiple payment providers (such as Stripe and local gateways). It handles complex payment lifecycles, dynamic provider routing, Pix/Boleto voucher generation, durable webhook inboxing, double-entry ledger accounting, and immutable audit logs.

---

## Key Features

- **Provider Agnostic & Isolation**: Unified protocol layer isolating raw provider payloads from canonical core logic (supports Fake Provider and Stripe Sandbox out-of-the-box).
- **Strict Idempotency**: Built-in request deduplication (`Idempotency-Key`) preventing double-charging on network retries.
- **Canonical Payment Capabilities**: Seamless support for Cards, **Pix** (Instant Copy & Paste / QR Code payloads), and **Boleto** Vouchers.
- **Dynamic Provider Routing**: Pure routing policy supporting merchant-level, currency-level, method-level, availability-based, and lowest-cost provider selection.
- **Durable Webhook Engine**: HMAC & SHA-256 signature validation, idempotent event processing, and dead-letter queue (DLQ) retry logic for consumer webhooks.
- **Financial Integrity & Audit**: Double-entry ledger accounting, granular partial refund tracking, dispute lifecycle management, and Datomic temporal time-travel auditing.

---

## Quick Start

### 1. Run with Docker

```powershell
# Copy example environment configuration
Copy-Item .env.example .env

# Build and launch the API container
docker compose up --build
```

### 2. Create a Payment

In a separate terminal, replace `<PAYMENT_ORCHESTRATOR_API_KEY>` with the value defined in your `.env` file:

```powershell
curl.exe -X POST http://localhost:8080/v1/payments `
  -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" `
  -H "X-Merchant-Id: demo-merchant" `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: demo-payment-001" `
  -d '{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"card"}'
```

### 3. Run Tests

To execute the test suite without installing local Clojure tooling:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test
```

---

## Project Documentation & References

- 📖 **[Technical Milestones & Detailed Reference](docs/MILESTONES.md)** – Comprehensive step-by-step setup, milestone history (M1–M24), and REPL commands.
- 🏗️ **[Architecture Overview](docs/ARCHITECTURE.md)** – Core domain boundaries, persistence layer design, and component isolation.
- 🔌 **[API Documentation](docs/API.md)** – Complete HTTP endpoint spec, request/response payloads for Card, Pix, Boleto, and Refunds.
- 🏢 **[Multi-Tenancy & Routing Policy](docs/MULTI-TENANCY.md)** – Configuration contracts for merchant isolation and smart provider routing.
- ⚡ **[Performance Profile](docs/PERFORMANCE.md)** – Benchmarks, latency metrics, and memory footprints.
- 🔒 **[Security Policy](SECURITY.md)** – Secret handling, tokenization, payload sanitization, and security guarantees.
- 📜 **[Release Notes](docs/RELEASE-NOTES-v1.0.0.md)** – v1.0.0 Release Candidate details.

---

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.
