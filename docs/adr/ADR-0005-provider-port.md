# ADR-0005 — Payment Gateway Port e Fake Provider

## Status

Aceito — Milestone M5.

## Decisão

O domínio depende do protocolo `PaymentGateway`, com operações de criar, consultar, cancelar e reembolsar. O adaptador fake é a implementação padrão de desenvolvimento e testes; não há dependência de Stripe/Asaas.

Resultados usam estados canônicos (`processing`, `requires-action`, `succeeded`, `failed`, `cancelled`), referência do provedor e status bruto. Erros carregam categoria canônica, `retryable?` e `outcome-known?`.

## Consequências

O fluxo persiste a referência/status do provedor separadamente após a criação local. Adapters reais poderão ser adicionados sem alterar handlers ou o domínio. Reconciliação de resultados desconhecidos e webhooks ficam para milestones posteriores.
