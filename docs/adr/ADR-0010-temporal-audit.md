# ADR-0010: Auditoria temporal de pagamentos com Datomic

## Status

Accepted.

## Contexto

O estado atual do pagamento não explica quando nem por qual requisição ele mudou. Manter uma tabela de auditoria manual duplicaria fatos já preservados pelo Datomic.

## Decisão

Consultas de auditoria usam `d/history` para os datoms de `:payment/status` e juntam a entidade de transação para obter `:db/txInstant` e metadata. A leitura histórica usa `d/as-of` com um instant ou ponto transacional Datomic.

As transações relevantes registram request/correlation id, actor técnico, source, reason e event type. A API expõe somente status, instante e contexto operacional; não expõe entity ids de pagamento, payload de provider ou dados do cliente.

## Consequências

O histórico é derivado de fatos imutáveis e mostra assertions e retractions. Consultas `asOf` são leituras consistentes do banco naquele ponto temporal, não reconstruções feitas pela aplicação.
