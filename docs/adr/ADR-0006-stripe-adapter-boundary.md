# ADR-0006 — Stripe Adapter Boundary

## Status

Aceito — Milestone M6.

## Decisão

O Stripe é integrado por uma implementação de `PaymentGateway` confinada a `provider/stripe/`. O adapter usa Payment Intents via HTTPS, recebe a chave secreta somente pela variável de ambiente `STRIPE_SECRET_KEY` e requer `STRIPE_TEST_PAYMENT_METHOD` para o sandbox.

Cada mutação recebe uma chave outbound distinta, derivada da operação interna: `payment-orchestrator-clj:<operation>:<operation-id>`. Respostas e erros externos são traduzidos para o contrato canônico antes de deixar o módulo.

## Consequências

O handler HTTP e o domínio continuam independentes de Stripe. O `client_secret` de uma ação adicional não é logado nem persistido pelo repositório atual. Webhooks, reconciliação e observabilidade detalhada permanecem fora do escopo do M6.
