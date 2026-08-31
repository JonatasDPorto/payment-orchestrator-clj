# Provider outage

1. Confirm the provider's status and affected operation without placing a second charge.
2. Treat timeouts as `unknown`, then reconcile using the provider reference and webhook inbox.
3. Do not fail over a payment to another provider automatically.
4. Preserve request and correlation IDs, notify the merchant, and record the incident outcome.
