# Payment Orchestrator in Clojure — Modelo de Dados Datomic

Este documento evolui junto com o roadmap.

Não implemente todo o schema no primeiro dia.

---

# 1. Princípios

## IDs de domínio

Entidades importantes devem ter IDs de domínio estáveis.

```text
:payment/id
:journal/id
:provider-event/id
```

Não exponha entity IDs do Datomic como identidade pública.

## Uniqueness

Use unicidade onde existe identidade de negócio real.

Exemplos:

```text
payment id
provider event id
idempotency key scoped
operation id
```

## Imutabilidade

Não tente reproduzir manualmente uma tabela de audit.

Aproveite o histórico de datoms.

## Transaction metadata

Use a entidade de transação para explicar mudanças.

---

# 2. Payment

Schema conceitual:

```clojure
[{:db/ident :payment/id
  :db/valueType :db.type/uuid
  :db/cardinality :db.cardinality/one
  :db/unique :db.unique/identity}

 {:db/ident :payment/customer-id
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one}

 {:db/ident :payment/amount
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one}

 {:db/ident :payment/currency
  :db/valueType :db.type/keyword
  :db/cardinality :db.cardinality/one}

 {:db/ident :payment/method
  :db/valueType :db.type/keyword
  :db/cardinality :db.cardinality/one}

 {:db/ident :payment/status
  :db/valueType :db.type/keyword
  :db/cardinality :db.cardinality/one}

 {:db/ident :payment/created-at
  :db/valueType :db.type/instant
  :db/cardinality :db.cardinality/one}]
```

---

# 3. Provider Payment

Não misture com Payment.

```text
Payment 1 --- N ProviderOperation/ProviderPayment
```

Isso permite retries, migrações e histórico.

Atributos:

```text
:provider-payment/id
:provider-payment/payment
:provider-payment/provider
:provider-payment/reference
:provider-payment/status
:provider-payment/raw-status
:provider-payment/created-at
```

Se `reference` não for globalmente única entre providers, crie identidade composta de aplicação ou entidade de key adequada.

---

# 4. Provider Operation

É útil separar tentativa/operação de representação do payment remoto.

```text
:provider-operation/id
:provider-operation/payment
:provider-operation/provider
:provider-operation/type
:provider-operation/idempotency-key
:provider-operation/status
:provider-operation/started-at
:provider-operation/completed-at
:provider-operation/error-category
```

Tipos:

```text
:create
:capture
:cancel
:refund
:fetch
```

Status técnicos:

```text
:started
:succeeded
:failed
:outcome-unknown
```

---

# 5. API Idempotency

```text
:idempotency/id
:idempotency/scope
:idempotency/key
:idempotency/request-hash
:idempotency/payment
:idempotency/created-at
```

A unicidade precisa considerar escopo.

Exemplo:

```text
merchant-id + endpoint + key
```

em versão multi-tenant.

---

# 6. Provider Event Inbox

```text
:provider-event/id
:provider-event/provider
:provider-event/external-id
:provider-event/type
:provider-event/status
:provider-event/payload
:provider-event/received-at
:provider-event/processed-at
:provider-event/payment
:provider-event/error
```

Status:

```text
:pending
:processing
:processed
:ignored
:failed
```

Mantenha payload bruto apenas se houver razão operacional/legal e política de retenção apropriada.

Uma alternativa é armazenar:

- campos necessários;
- hash;
- subset redigido.

Não guarde dados sensíveis "porque pode ser útil".

---

# 7. Ledger

## Ledger Account

```text
:ledger-account/id
:ledger-account/code
:ledger-account/type
:ledger-account/currency
```

## Journal

```text
:journal/id
:journal/payment
:journal/type
:journal/created-at
```

## Posting

```text
:posting/id
:posting/journal
:posting/account
:posting/side
:posting/amount
:posting/currency
```

`side`:

```text
:debit
:credit
```

Invariante não deve depender apenas de leitura posterior. A operação de criação de journal precisa validar balanceamento no boundary correto.

---

# 8. Reconciliation

```text
:reconciliation/id
:reconciliation/payment
:reconciliation/provider
:reconciliation/reason
:reconciliation/local-status
:reconciliation/remote-status
:reconciliation/result
:reconciliation/created-at
```

Resultados:

```text
:matched
:corrected
:mismatch
:manual-review
```

---

# 9. Event Relay Checkpoint

```text
:relay/id
:relay/consumer-name
:relay/last-t
:relay/updated-at
```

Não trate checkpoint como garantia mágica de exactly-once.

Assuma reprocessamento.

---

# 10. Transaction metadata

Schema conceitual:

```text
:tx/request-id
:tx/correlation-id
:tx/actor
:tx/source
:tx/reason
:tx/event-type
:tx/payment-id
```

Exemplo:

```clojure
[[:db/add "datomic.tx" :tx/source :source/webhook]
 [:db/add "datomic.tx" :tx/request-id request-id]
 [:db/add payment-eid :payment/status :payment.status/paid]]
```

---

# 11. Temporal queries

## Current

```clojure
(d/pull (d/db conn) '[*] [:payment/id id])
```

## As of

```clojure
(let [db (d/db conn)
      historical (d/as-of db tx-or-time)]
  ...)
```

Quando você possui `t`/transaction id, prefira-o para precisão transacional.

## History

```clojure
(def history-db (d/history (d/db conn)))
```

Query datoms:

```text
entity
attribute
value
transaction
added?
```

Isso permite reconstruir timeline.

---

# 12. `with` para simulação

Após o core estar pronto, Datomic `with` pode ser usado para simulações sem persistência.

Exemplos:

```text
what-if risk policy
what-if ledger postings
what-if transition
```

Não transforme isso em feature obrigatória antes do MVP.

---

# 13. Queries que devem existir

Até v1:

```text
payment-by-id
payments-by-status
provider-payment-by-reference
pending-provider-events
payment-history
idempotency-by-key
unresolved-provider-operations
journals-by-payment
pending-reconciliation
relay-checkpoint
```

---

# 14. Índices e query thinking

Datomic não deve ser tratado como SQL com sintaxe diferente.

Para cada query crítica:

1. escreva o padrão de acesso;
2. examine atributos e índices;
3. use query stats/io stats quando necessário;
4. evite pull indiscriminado;
5. mantenha dataset de benchmark realista.

---

# 15. Migração de schema

Datomic tem características diferentes de migrations SQL.

Trate mudanças como dados versionados e documentação.

Crie namespace:

```text
datomic/schema/
  v001.clj
  v002.clj
```

ou uma estrutura equivalente que permita saber o que foi aplicado.

Não "edite o passado" silenciosamente no repositório.

---

# 16. Dados sensíveis

Não armazene:

```text
card PAN
CVV
provider secret
webhook secret
```

Evite armazenar payload externo completo se ele pode conter PII desnecessária.

---

# 17. Test database strategy

Cada test suite de integração deve obter database limpa.

Funções auxiliares:

```clojure
(with-test-db f)
```

responsável por:

```text
create
schema
test
delete
```

Isso reduz dependência entre testes.

---

# 18. Perguntas de revisão de modelagem

Antes de adicionar atributo:

- isto pertence ao domínio ou a um provider?
- precisa ser cardinality one ou many?
- possui identidade real?
- precisa de unique?
- será consultado frequentemente?
- o histórico desta informação importa?
- é PII?
- por quanto tempo precisa existir?
- deve ficar na transaction metadata em vez da entidade?
