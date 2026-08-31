# v1.0.0 release notes

## Highlights

- Provider-agnostic canonical payment API with Fake Provider and Stripe sandbox adapter.
- Datomic persistence, API idempotency, temporal audit, double-entry ledger, reconciliation, webhook inbox, and Datomic-log Kafka relay.
- Structured logs, metrics, API-key hardening, request limits, runbooks, and reproducible performance profiles.

## Known limitations

- Asaas adapter was intentionally skipped and is not included in v1.0.0.
- Kafka relay is at-least-once; consumers must deduplicate.
- Rate limiting is per process; production requires edge controls.
- This repository is not PCI DSS certified.

## Release checklist

- Run all Docker test commands in the README.
- Review Dependabot/security alerts and `SECURITY.md`.
- Create and push annotated tag `v1.0.0` after maintainer approval.
- Create the GitHub release from these notes and attach the recorded demo link when available.
