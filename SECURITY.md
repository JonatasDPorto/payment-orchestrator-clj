# Security policy

## What this project protects

- The public API requires a service API key supplied as `Authorization: Bearer <key>` (the legacy `X-API-Key` header is also accepted for local compatibility). Requests without a valid key receive a generic `401` response.
- Stripe webhooks are exempt from service-key authentication only because they are authenticated separately with Stripe's signature over the unmodified request body and a five-minute timestamp tolerance.
- Requests with a declared body larger than 1 MiB are rejected with `413`; each process also applies a fixed-window limit of 60 requests per minute per client, method, and route.
- Structured logging redacts authorization values, provider signatures, tokens, secrets, passwords, and API keys. Payment requests are validated with a closed schema.
- The API deliberately accepts canonical payment fields, not raw card data. PAN, CVV, magnetic-stripe data, and provider secrets are rejected or must never be sent.

## Authorization model

This is a single-merchant service-to-service API. A valid service key authorizes access to all current `/v1` and `/metrics` routes. There is no user or tenant identity in the data model, so tenant-scoped authorization is intentionally not claimed. Adding multi-tenant access requires a separate authorization design before exposing merchant data.

## Secrets and personal data

Keep `PAYMENT_ORCHESTRATOR_API_KEY`, `STRIPE_SECRET_KEY`, and `STRIPE_WEBHOOK_SECRET` in environment variables. `.env` is for local development only and is ignored by Git; production deployments must use their platform's secret manager. Never put real values in source, fixtures, exception data, logs, issues, or documentation.

The current PII inventory is limited to the caller-supplied `customerId`, which is persisted as an application identifier. It must not be logged. The service does not store Stripe webhook payloads; it stores only the event's operational fields and SHA-256 payload hash.

## What this project does not protect

This reference implementation is not PCI DSS certified and is not a complete production security program. It does not provide distributed rate limiting, WAF/DDoS protection, mTLS/OAuth, user accounts, tenant authorization, key rotation, encryption/key management, or infrastructure TLS termination. Deploy those controls at the platform edge and revisit this policy before production use.

## Threat model (simplified STRIDE)

| Threat | Mitigation | Remaining risk |
| --- | --- | --- |
| Spoofing | Service API key; Stripe HMAC verification using constant-time comparison | Key theft; rotate keys and use a secret manager |
| Tampering | Signed raw Stripe body; TLS must be terminated by deployment | Infrastructure TLS/configuration is outside this repository |
| Repudiation | Idempotency records, audit history, webhook event identity, request/correlation IDs | Actor identity is service-level only |
| Information disclosure | Closed DTO schema, redacted logs, no raw webhook payload persistence | `customerId` remains application PII in persistence |
| Denial of service | 1 MiB declared-body limit and per-process fixed-window limit | Limits are not distributed; edge controls are required |
| Elevation of privilege | One explicit service role and no user/tenant claims | A multi-tenant model must introduce scoped authorization |

## Dependency monitoring

GitHub Dependabot monitors Maven dependencies weekly through `.github/dependabot.yml`. Review security alerts and dependency updates before merging. A release check must also inspect the dependency tree with `clojure -Stree` and resolve reported vulnerabilities through the dependency's official advisory.

## Reporting a vulnerability

Do not open a public issue containing an exploit, credential, payment data, or sensitive log. Contact the repository owner privately through the GitHub account listed on the repository and include a minimal reproduction, affected revision, impact, and suggested mitigation. The maintainer should acknowledge the report, rotate exposed credentials if needed, and coordinate disclosure after a fix is available.
