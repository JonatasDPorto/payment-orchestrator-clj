# ADR-0003 — Datomic como sistema de registro

## Status

Accepted

## Context

Payment Orchestrator in Clojure precisa de uma fonte de verdade para pagamentos que preserve fatos históricos e possibilite auditoria temporal nos milestones posteriores.

## Decision

Usaremos Datomic Local e sua Client API no desenvolvimento e nos testes de integração. O acesso fica atrás de um repositório de pagamentos, com schema versionado como dados. IDs públicos de pagamento são atributos únicos; metadata da transação registra contexto de escrita.

## Alternatives

- Banco relacional com tabela de auditoria manual.
- Expor a Client API diretamente à camada de aplicação.

## Consequences

### Positive

- Fatos imutáveis e histórico nativo apoiam auditoria e consultas temporais futuras.
- O domínio continua independente de Datomic.
- Testes usam bancos isolados em memória.

### Negative

- Datomic Local é apropriado para desenvolvimento e aplicações pequenas de processo único, não para a topologia final de produção.
- O time precisa dominar schema, transações e queries Datomic.
