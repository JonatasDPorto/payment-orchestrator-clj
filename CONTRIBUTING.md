# Contributing

Use Java 21 and Docker. Copy `.env.example` to `.env`, set a private local API key, then run `docker compose up --build`.

Before opening a pull request, run the unit and integration suites shown in the README. Keep provider-specific concepts inside their adapter, add tests for behavior changes, and never commit credentials, PAN, CVV, or production data.

Use conventional, focused commits. Report vulnerabilities privately as described in `SECURITY.md`, not through public issues.
