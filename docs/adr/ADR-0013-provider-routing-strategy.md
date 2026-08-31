# ADR-0013: Provider routing strategy

## Status

Accepted.

## Context

After merchant isolation, provider selection cannot remain a process-wide implementation detail. The service needs policy choices by merchant, currency, payment method, availability, and cost while preserving the provider-neutral public API.

## Decision

Provider routing is a pure `select-provider` function. It receives canonical payment context and configured provider descriptors. Merchant, currency, payment method, and default rules have deterministic precedence. A lowest-cost strategy is supported for compatible available providers. Gateway construction remains at the runtime composition boundary.

A configured provider that is unavailable or incompatible raises a canonical unavailable error. The system never silently switches a payment operation to another provider, including when the prior operation outcome is unknown.

## Alternatives

- Let each HTTP client choose a provider: rejected because it leaks infrastructure and undermines a stable public contract.
- Retry an unavailable route with another provider: rejected because it can duplicate a financial effect.
- Put routing conditionals in each adapter: rejected because policy would no longer be testable as a pure application decision.

## Consequences

Routing behavior is small, deterministic, and unit-tested without Datomic or network access. Adding a real second provider requires only registering its adapter and descriptor; it does not change the consumer request. An unavailable selected provider can reduce availability, intentionally favoring payment safety over an unsafe fallback.
