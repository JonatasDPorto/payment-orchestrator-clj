# ADR-0016: Subscription, Invoice, and Payment boundaries

## Status

Accepted.

## Context

Recurring billing has a lifecycle distinct from a payment attempt. Treating a subscription as a field on `Payment` would conflate a standing commercial agreement with a single collection attempt.

## Decision

Model `Subscription` and `Invoice` as separate aggregates. A subscription holds cadence and recurring amount; an invoice represents one amount due and may reference the payment created to collect it. Neither aggregate embeds provider-specific recurring concepts or a payment payload.

## Consequences

Payment remains independently idempotent and provider-agnostic. Future scheduling, provider subscription adapters, retries, and invoice collection can evolve without changing the payment aggregate.
