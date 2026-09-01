# ADR-0018: Disputes are a separate bounded context

## Status

Accepted.

## Context

A dispute/chargeback is raised by a payment provider after a payment. Its
lifecycle, evidence, and final outcome are not payment-state transitions.

## Decision

Model disputes as immutable-provider-referenced aggregates linked to a payment
ID. They have their own state machine (`needs-response`, `under-review`,
`won`, `lost`) and Datomic uniqueness on the provider reference. The Payment
aggregate is not altered by creating or resolving a dispute.

## Consequences

The payment domain stays stable and a provider webhook adapter can evolve the
dispute aggregate without leaking chargeback terminology into payment APIs.
