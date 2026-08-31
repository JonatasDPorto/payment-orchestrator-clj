# ADR-0004 — Idempotência na criação via API

## Status

Accepted

## Context

Consumidores podem repetir `POST /v1/payments` após timeout, falha de rede ou retry automático. Criar dois pagamentos para o mesmo comando é um efeito de negócio incorreto.

## Decision

`POST /v1/payments` exige `Idempotency-Key`. O comando canônico é normalizado e recebe hash SHA-256 determinístico. O pagamento e o registro de idempotência são persistidos na mesma transação Datomic, com unicidade de valor em `:idempotency/key`.

Uma chave existente com o mesmo hash retorna o pagamento original. Com hash diferente retorna `idempotency_conflict`. Conflitos de unicidade em transações concorrentes são relidos e transformados nesses resultados canônicos.

## Alternatives

- Deduplicar apenas em memória.
- Criar o pagamento e o registro de idempotência em transações separadas.
- Reutilizar o mecanismo de idempotência do provider.

## Consequences

### Positive

- Retries do consumidor têm um único efeito de negócio.
- A garantia funciona entre threads e persiste após reinício.
- Idempotência do consumidor permanece separada de providers e webhooks.

### Negative

- A chave fica global neste milestone; o escopo por merchant/endpoint será necessário quando multi-tenancy existir.
- A chave deve ser retida por uma política futura de expiração.
