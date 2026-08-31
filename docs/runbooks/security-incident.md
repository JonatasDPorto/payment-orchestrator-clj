# Suspected credential or data exposure

1. Stop sharing logs, payloads, or screenshots that may contain the value.
2. Rotate the affected API key or provider secret in the secret manager and restart only the affected service instances.
3. Review access logs and audit records using request IDs; do not copy secrets into the incident record.
4. Remove the exposed value from collaboration systems where possible and document impact, containment, and follow-up.
