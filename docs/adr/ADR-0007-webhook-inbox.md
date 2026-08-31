# ADR-0007 — Stripe Webhook Inbox

## Status

Aceito — Milestone M7.

## Decisão

`POST /webhooks/stripe` lê o corpo original, valida `Stripe-Signature` usando HMAC-SHA256 e tolerância de cinco minutos, e só então interpreta JSON. O endpoint grava uma inbox Datomic com unicidade em `stripe:<event-id>`, responde rapidamente e agenda o processamento de eventos pendentes.

O inbox armazena identificadores operacionais e SHA-256 do corpo, não o payload completo. Eventos desconhecidos são marcados como ignorados; falhas de processamento permanecem pendentes com um código de erro, permitindo reprocessamento após reinício.

## Consequências

Entregas at-least-once não duplicam transições de negócio. A assinatura e nomes de eventos Stripe ficam confinados ao módulo Stripe/webhook. Não há webhook de outro provider, retries avançados ou reconciliação neste milestone.
