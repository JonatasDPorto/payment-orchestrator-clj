# ADR-0017: Refund aggregate invariant

## Status

Accepted.

## Context

A captured payment can be refunded more than once. A refund flow must never
allow the sum of successful refunds to exceed the captured amount, and it must
not expose provider-specific identifiers through the public API.

## Decision

Refunds are separate immutable records linked to a payment. A new refund is accepted only when the aggregate refund total remains less than or equal to the captured payment amount. Payment status becomes partially refunded or refunded from that aggregate total.

The API accepts an amount in minor BRL units at `POST /v1/payments/:id/refunds`.
The provider adapter receives the payment's provider reference and the canonical
amount. A reconciliation snapshot compares locally recorded refunds with a
provider-supplied refund list; a mismatch is recorded for manual investigation,
never silently corrected.

## Alternatives

- Store a mutable refunded-total on Payment: rejected because individual refunds
  and provider references would not be auditable.
- Trust the provider's current balance alone: rejected because a local audit
  trail and explicit mismatch handling are required.

## Consequences

Positive: partial and multiple refunds are auditable, and the aggregate
invariant is independently property- and concurrency-tested. Negative: refund
records and reconciliation snapshots add a small persistence surface.
