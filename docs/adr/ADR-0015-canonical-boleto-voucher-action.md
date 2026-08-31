# ADR-0015: Canonical Boleto voucher action

## Status

Accepted.

## Context

Boleto confirmation creates a voucher but does not settle the payment. Stripe represents the voucher in `next_action.boleto_display_details` with provider-specific field names. The public API needs the voucher number, an accessible voucher URL, optional PDF, and expiry without exposing a Stripe payload.

## Decision

Persist a `:boleto/voucher` payment action with the canonical number in `:payment-action/payload`, voucher URL in `:payment-action/hosted-instructions-url`, optional PDF URL in `:payment-action/document-url`, and expiry in `:payment-action/expires-at`. The payment remains `requires-action` until an authenticated provider webhook changes its status.

## Alternatives

- Return the complete Stripe `next_action` object: rejected because it leaks provider implementation.
- Mark a generated voucher as paid: rejected because Boleto is asynchronous.
- Store customer billing data: rejected because it is only needed transiently to create the voucher.

## Consequences

Consumers have a provider-neutral voucher contract. The public API has a new Boleto request shape requiring the customer information Stripe needs for server-side confirmation. Boleto refunds are deliberately not exposed as a Boleto capability because Stripe documents that Boleto payments cannot be refunded.
