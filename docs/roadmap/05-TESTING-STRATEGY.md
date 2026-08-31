# Payment Orchestrator in Clojure — Estratégia de Testes

A qualidade do projeto deve ser visível nos testes.

Cobertura percentual não é a meta principal.

A meta é provar propriedades e failure modes importantes.

---

# 1. Pirâmide adaptada ao projeto

```text
                  E2E
                /     \
          Integration
         /             \
     Contract       Concurrency
      /                 \
   Property           Unit
```

Não precisa ser uma pirâmide geométrica perfeita.

---

# 2. Unit tests

Use para domínio puro.

Testar:

```text
money
state transitions
refund invariants
routing decisions
error classification
ledger validation
```

Devem ser rápidos.

Não subir Datomic.

Não fazer network.

---

# 3. Property-based tests

Essenciais para portfólio sênior.

## Payment state machine

Gerar sequências de transições.

Provar invariantes.

## Money

```text
amount never becomes floating point
refund <= captured
```

## Ledger

```text
sum(debits) == sum(credits)
```

## Idempotency

```text
N same commands -> one business result
```

---

# 4. Datomic integration tests

Testar:

- schema;
- unique;
- transact;
- pull;
- queries;
- transaction metadata;
- history;
- as-of.

Use database isolada.

---

# 5. Provider contract tests

A mesma suite para cada adapter.

Não apenas "HTTP returned 200".

Valide semântica canônica.

---

# 6. Mapper golden tests

Input externo conhecido:

```json
{...}
```

Output esperado canônico:

```clojure
{...}
```

Muito úteis porque provider payloads mudam.

---

# 7. Webhook tests

Tabela mínima:

| Cenário | Esperado |
|---|---|
| valid | persist + ack |
| invalid signature/token | reject |
| duplicate | ack, one processing |
| unknown type | safe handling |
| malformed | reject/record |
| processor crash | event recoverable |
| event for unknown payment | observable failure |

---

# 8. Concurrency tests

## API idempotency

100 threads/requests.

Mesma key.

Critério:

```text
one payment
```

## Event processing

Mesmo event processado simultaneamente por workers.

Critério:

```text
one effective business transition
```

## Refund

Dois partial refunds concorrentes não podem ultrapassar captured amount.

---

# 9. Fake provider for failure injection

O fake é ferramenta de teste séria.

Modes:

```text
success
decline
timeout-before-processing
commit-then-timeout
rate-limit
server-error
malformed-response
slow-success
```

Possibilita testar cenários quase impossíveis de reproduzir consistentemente em sandbox real.

---

# 10. Reconciliation tests

Cenários:

### Local pending / remote paid

Esperado:

```text
correct to paid
```

### Local paid / remote paid

```text
matched
```

### Local paid / remote missing

```text
manual review / mismatch
```

de acordo com política.

### Remote API unavailable

Não alterar estado de forma destrutiva.

---

# 11. Ledger tests

## Balanced

```text
Dr 100
Cr 100
```

aceito.

## Unbalanced

```text
Dr 100
Cr 99
```

rejeitado.

## Duplicate event

Um evento duplicado não cria outro journal com a mesma identidade econômica.

---

# 12. Temporal tests

Sequência:

```text
T1 created
T2 processing
T3 paid
```

Provar:

```text
current = paid
as-of T1 = created
as-of T2 = processing
history contains all transitions
```

---

# 13. Event relay tests

Simule:

```text
tx T1
tx T2
publish T1
crash
restart
```

O sistema pode republicar T1, mas não perder T2.

Consumidor deve deduplicar.

---

# 14. Load testing

Somente após comportamento correto.

Profiles:

```text
read-heavy
write-heavy
webhook burst
reconciliation scan
history queries
```

Resultados versionados em docs.

---

# 15. Security tests

- invalid API key;
- wrong tenant;
- forged Stripe signature;
- invalid Asaas token;
- webhook replay;
- body too large;
- injection-like inputs;
- log redaction;
- secret exposure.

---

# 16. Test aliases

Sugestão:

```text
:test          unit + fast
:test:integration
:test:contract
:test:e2e
:test:load
```

CI:

```text
PR:
  unit
  property
  datomic integration
  fake provider contract

main/nightly:
  Stripe sandbox
  Asaas sandbox
  heavier concurrency
```

Adapte conforme custo/credenciais.

---

# 17. Test naming

Nomes devem contar regra:

Bom:

```text
duplicate-webhook-does-not-create-second-ledger-entry
```

Ruim:

```text
test-webhook-3
```

---

# 18. Arrange / Act / Assert

Mantenha cenário legível.

Testes também são documentação.

---

# 19. Fixtures externas

Nunca inclua secrets.

Sanitize payloads reais antes de commit.

---

# 20. Definition of Done de teste

Uma feature financeira não está pronta até existir teste para pelo menos:

```text
happy path
validation failure
duplicate
provider failure
retry/timeout quando aplicável
audit evidence
```

---

# 21. Testes que devem aparecer no README

Mostre 4 ou 5 exemplos impressionantes:

```text
100 concurrent identical idempotent requests -> 1 payment

duplicate webhook -> 1 financial effect

commit-then-timeout -> reconciliation recovers state

historical query reconstructs previous payment status

arbitrary generated journals remain balanced
```

Esses testes vendem o projeto melhor que uma badge de 97% coverage.
