# ADR-0014: Canonical Pix action

## Status

Accepted.

## Context

Pix is a payment capability that requires a customer to receive a QR code or copy-and-paste payload before the payment completes. Stripe's API represents this inside a provider-specific `next_action` structure, which must not leak through the public API or become persisted provider data.

## Decision

The canonical payment model persists a component action for Pix QR data: `:payment-action/type`, `:payment-action/payload`, optional `:payment-action/qr-code-url`, optional `:payment-action/hosted-instructions-url`, and `:payment-action/expires-at`. The public API exposes the same provider-neutral values. The Stripe mapper reads only those Pix QR-code fields from a provider response.

## Alternatives

- Return Stripe's complete `next_action`: rejected because it leaks provider concepts and unnecessary data.
- Omit provider-supplied QR or hosted-instructions URLs: rejected because consumers need renderable instructions when the provider offers them.
- Store generic provider payload JSON: rejected because it would create a provider-specific backdoor and increase sensitive-data retention.

## Consequences

Consumers receive a stable Pix action regardless of provider. Action values are persisted only when needed to complete a Pix payment; raw provider action objects remain outside persistence and API responses.
