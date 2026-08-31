# ADR-0012: Relay de eventos a partir do log Datomic

## Status

Accepted.

## Decisão

Eventos públicos são derivados das transações Datomic, não publicados diretamente pelo fluxo de pagamento. O relay percorre `tx-range`, publica no Kafka e só grava o checkpoint após confirmar a publicação da transação.

O resultado é at-least-once: uma falha depois de publicar e antes do checkpoint pode republicar o mesmo `eventId`. Consumidores devem deduplicar por esse identificador determinístico. O relay não promete exactly-once.
