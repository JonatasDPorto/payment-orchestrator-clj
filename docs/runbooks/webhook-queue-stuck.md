# Webhook queue stuck

1. Verify that the webhook signature secret is configured without printing it.
2. Inspect pending provider-event records and the operational error code.
3. Restore the dependent service, then rerun the idempotent pending-event processor.
4. Confirm duplicate delivery does not create another financial effect.
