# ADR-0008 — Validação do segundo provider

## Status

Accepted — M8.

## Context

Adicionar Asaas precisava provar que o contrato canônico não era específico da Stripe.

## Decision

Asaas implementa o mesmo `PaymentGateway`, a mesma contract suite e a mesma inbox de webhooks. A API e o domínio continuam usando referência, status e ações canônicas. Auth `access_token`, token de webhook, formatos de cobrança e nomes de eventos ficam em `provider/asaas/`.

## Consequences

Routing escolhe Stripe, Fake ou Asaas sem alterar requests consumidores. Asaas requer customer nativo e due date; esses detalhes são responsabilidade do adapter/boundary. Capabilities expostas são apenas create/fetch/cancel/refund e card/Pix/boleto.
