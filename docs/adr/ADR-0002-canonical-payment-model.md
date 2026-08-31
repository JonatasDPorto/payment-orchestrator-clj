# ADR-0002 — Modelo canônico de pagamento

## Status

Accepted

## Context

O Payment Orchestrator in Clojure precisa representar pagamentos sem expor conceitos ou ciclos de vida de um provider específico. A primeira versão do domínio precisa ser testável sem banco, HTTP ou rede.

## Decision

Pagamentos são mapas Clojure canônicos. Valores monetários usam unidade mínima inteira e moeda explícita. Estados e transições formam uma state machine explícita. Eventos de domínio são dados anexados ao pagamento, sem publicação nesta etapa; timestamps são recebidos do chamador para manter as funções determinísticas.

## Alternatives

- Modelar diretamente estados da Stripe ou Asaas.
- Usar `double` para valores monetários.
- Permitir qualquer mudança de status.

## Consequences

### Positive

- Regras são testáveis sem infraestrutura.
- O contrato permanece independente de provider.

### Negative

- O conjunto inicial suporta somente BRL e cartão; novas capabilities serão adicionadas quando o roadmap as exigir.
