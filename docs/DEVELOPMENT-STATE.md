# Development state

- Milestone: M19 — PIX
- Status: IN_PROGRESS
- Completed: Canonical Pix QR action with copy-and-paste payload, optional QR/hosted-instructions URLs and expiry; Stripe `confirm=true` PaymentIntent mapper; test CPF and documented test-email request coverage; Fake Provider/API/Datomic support; asynchronous `payment_intent.succeeded` and `payment_intent.payment_failed` processing with webhook idempotency; and generic Pix PaymentIntent refund request coverage.
- Remaining: Stripe's real test API must accept a Pix PaymentIntent for this account so the QR-generation and test-email scenarios can be validated end-to-end.
- Last tests: Unit suite passed (63 tests, 177 assertions); integration suite passed (18 tests, 62 assertions). The live Pix sandbox call using `.env` reached Stripe and returned HTTP 400, code `payment_intent_invalid_parameter`, request ID `req_2I5Qqs4IRiNkdE`, specifically rejecting `payment_method_types[]=pix` for this account.
- Decision: The adapter uses the requested direct PaymentIntent flow (`payment_method_types[]=pix`, `currency=brl`, `confirm=true`, and `payment_method_data` with test CPF). It does not misrepresent account-ineligibility as a successful Pix test.
- Next action: obtain Stripe account/test-mode eligibility for Pix or a test account where the direct Pix PaymentIntent request is accepted, then run `docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-stripe-pix-sandbox`.
