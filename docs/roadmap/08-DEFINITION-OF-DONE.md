# Payment Orchestrator in Clojure — Definition of Done e Checklist de Execução

Use este arquivo como checklist operacional durante o desenvolvimento.

---

# DoD universal de uma feature

Uma feature está pronta quando:

- [ ] existe comportamento de negócio definido;
- [ ] happy path testado;
- [ ] erro de validação testado;
- [ ] failure mode externo testado quando aplicável;
- [ ] idempotência avaliada;
- [ ] concorrência avaliada;
- [ ] logs relevantes;
- [ ] métricas relevantes;
- [ ] nenhum secret/PII indevido em logs;
- [ ] documentação atualizada;
- [ ] API contract atualizado se mudou;
- [ ] ADR criado se houve decisão significativa;
- [ ] `clojure -M:test` verde;
- [ ] demo manual reproduzível.

---

# Checklist M0

- [ ] deps.edn
- [ ] src/
- [ ] test/
- [ ] dev/
- [ ] config
- [ ] test alias
- [ ] CI
- [ ] README boot instructions

---

# Checklist M1

- [ ] Money
- [ ] Payment
- [ ] statuses
- [ ] transitions
- [ ] invalid transition
- [ ] domain events
- [ ] unit tests
- [ ] property test

---

# Checklist M2

- [ ] Datomic Local
- [ ] schema
- [ ] connection lifecycle
- [ ] repository
- [ ] mapping
- [ ] uniqueness
- [ ] transaction metadata
- [ ] integration tests

---

# Checklist M3

- [ ] POST /v1/payments
- [ ] GET /v1/payments/:id
- [ ] Malli DTO
- [ ] canonical error
- [ ] HTTP tests
- [ ] curl demo

---

# Checklist M4

- [ ] Idempotency-Key
- [ ] request hash
- [ ] same request replay
- [ ] conflict
- [ ] concurrent duplicate test

---

# Checklist M5

- [ ] PaymentGateway
- [ ] canonical command
- [ ] canonical result
- [ ] canonical errors
- [ ] capabilities
- [ ] Fake Gateway
- [ ] contract suite

---

# Checklist M6

- [ ] Stripe client
- [ ] Stripe adapter
- [ ] mapper
- [ ] errors
- [ ] timeout
- [ ] outbound idempotency
- [ ] sandbox test
- [ ] contract suite green

---

# Checklist M7

- [ ] webhook endpoint
- [ ] raw body preserved
- [ ] signature validation
- [ ] inbox persistence
- [ ] unique external event id
- [ ] async processing
- [ ] duplicate test
- [ ] crash recovery test

---

# Checklist M8

- [ ] Asaas client
- [ ] adapter
- [ ] mapper
- [ ] errors
- [ ] webhook auth
- [ ] event idempotency
- [ ] contract suite
- [ ] provider replacement demo
- [ ] no consumer API change

---

# Checklist M9

- [ ] ledger account
- [ ] journal
- [ ] posting
- [ ] debit/credit invariant
- [ ] property tests
- [ ] duplicate prevention
- [ ] payment ledger query

---

# Checklist M10

- [ ] payment history
- [ ] as-of
- [ ] tx metadata
- [ ] timeline
- [ ] audit endpoint
- [ ] historical tests

---

# Checklist M11

- [ ] error taxonomy
- [ ] outcome-known?
- [ ] retry policy
- [ ] fake commit-then-timeout
- [ ] reconciliation query
- [ ] reconciliation worker
- [ ] mismatch behavior
- [ ] audit

---

# Checklist M12

- [ ] event schema
- [ ] transaction markers
- [ ] tx-range relay
- [ ] Kafka producer
- [ ] checkpoint
- [ ] duplicate-safe consumer
- [ ] restart test
- [ ] Kafka unavailable test

---

# Checklist M13

- [ ] structured logs
- [ ] request id
- [ ] correlation id
- [ ] payment id correlation
- [ ] metrics
- [ ] traces
- [ ] dashboard
- [ ] log redaction test

---

# Checklist M14

- [ ] API auth
- [ ] authorization model
- [ ] webhook auth
- [ ] secrets management
- [ ] rate limits
- [ ] PII inventory
- [ ] SECURITY.md
- [ ] threat model
- [ ] runbooks

---

# Checklist M15

- [ ] generated dataset
- [ ] load scripts
- [ ] p50
- [ ] p95
- [ ] p99
- [ ] error rate
- [ ] webhook burst
- [ ] provider latency scenario
- [ ] PERFORMANCE.md

---

# Checklist M16

- [ ] Docker
- [ ] one-command local run
- [ ] Terraform if justified
- [ ] CI/CD
- [ ] final README
- [ ] architecture diagram
- [ ] demo script
- [ ] video
- [ ] clean repository
- [ ] v1.0.0 tag

---

# Before every release

- [ ] full test suite
- [ ] dependency/security scan
- [ ] no secrets in git history
- [ ] release notes
- [ ] migrations/schema reviewed
- [ ] rollback/recovery considered
- [ ] provider API version/config reviewed
- [ ] docs current

---

# "Não avançar" conditions

Pare a evolução e corrija antes da próxima fase se:

- [ ] há teste flaky;
- [ ] existe dependência circular entre domain e infrastructure;
- [ ] provider concept vazou para public API sem justificativa;
- [ ] duplicação pode causar efeito financeiro duplicado;
- [ ] timeout é tratado como certeza sem evidência;
- [ ] existe secret no repositório;
- [ ] feature não possui demo reproduzível;
- [ ] README está mentindo sobre garantia do sistema.

---

# Weekly review

Uma vez por ciclo de desenvolvimento, responda:

```text
What became simpler?
What became more coupled?
Which invariant is not tested?
Which failure mode is not simulated?
Which abstraction exists without two real use cases?
What would break if Stripe disappeared tomorrow?
What would a new Asaas-like provider force us to change?
Can a fresh clone run the project?
```

---

# Definition of v1.0

Payment Orchestrator in Clojure v1.0 só existe quando:

- [ ] Stripe funciona em sandbox;
- [ ] Asaas funciona em sandbox;
- [ ] mesma API pública funciona com ambos;
- [ ] request idempotency;
- [ ] webhook idempotency;
- [ ] safe unknown-outcome handling;
- [ ] reconciliation;
- [ ] ledger;
- [ ] Datomic temporal audit;
- [ ] event relay;
- [ ] observability;
- [ ] security baseline;
- [ ] tests;
- [ ] reproducible local environment;
- [ ] portfolio-quality docs.

Se o objetivo imediato for colocar seu outro software para funcionar, faça um **MVP release anterior** em M8 e use os milestones seguintes para endurecer a plataforma.
