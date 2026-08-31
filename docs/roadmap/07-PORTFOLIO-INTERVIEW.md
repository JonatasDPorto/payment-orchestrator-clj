# Payment Orchestrator in Clojure — Como transformar o projeto em portfólio de nível sênior

Código bom escondido num repositório confuso perde muito valor.

Este documento define como apresentar o projeto.

---

# 1. Headline

Sugestão:

> **Payment Orchestrator in Clojure — Provider-agnostic payment orchestration, temporal audit and financial ledger built with Clojure + Datomic.**

Subheadline:

> Integrates Stripe and Asaas behind a stable domain contract with idempotent processing, reconciliation, double-entry ledger and durable event delivery.

---

# 2. Primeira tela do README

Mostrar imediatamente:

```text
Clojure
Datomic
Stripe
Asaas
Kafka
OpenTelemetry
```

Depois:

```text
✓ Provider-agnostic API
✓ Idempotent payment creation
✓ Idempotent webhook inbox
✓ Double-entry ledger
✓ Temporal audit
✓ Reconciliation
✓ Datomic transaction-log relay
✓ Property-based tests
```

---

# 3. Diagrama

Um diagrama simples.

Não coloque 40 caixas.

```text
Consumer
   |
Payment Orchestrator in Clojure
   |
   + Datomic
   |
   + Payment Gateway Port
       |
       + Stripe
       + Asaas
```

Depois um segundo diagrama para async.

---

# 4. Seção "Why this exists"

Explique problema real:

> Aplicações normalmente acabam acopladas ao vocabulário e lifecycle de um payment provider. Payment Orchestrator in Clojure cria uma API canônica e mantém integrações externas atrás de adapters, permitindo trocar provider sem reescrever consumidores.

---

# 5. Seção "Hard problems"

Destaque:

## Ambiguous provider outcomes

```text
provider committed
network timed out
what now?
```

## Duplicate webhooks

```text
at-least-once delivery
```

## Dual write

```text
Datomic + Kafka
```

## Financial invariants

```text
debits == credits
```

## Time travel

```text
what did we know at T?
```

---

# 6. Seção "What I deliberately did NOT do"

Essa seção demonstra julgamento.

Exemplo:

```text
- no premature microservice split
- no blind failover between payment processors
- no direct provider payload in public API
- no card PAN storage
- no fake "exactly once" claim
```

---

# 7. ADRs

Link visível no README.

Um recrutador técnico pode abrir:

```text
Why Datomic?
Why modular monolith?
Why inbox?
Why not automatic provider fallback?
```

e imediatamente perceber profundidade.

---

# 8. Demo de 5 minutos

## 00:00

Subir stack.

## 00:30

Criar payment via Stripe.

## 01:00

Mostrar Datomic.

## 01:30

Repetir request idempotente.

## 02:00

Duplicar webhook.

## 02:30

Mostrar historical query.

## 03:00

Trocar provider para Asaas.

## 03:30

Mesmo curl, mesma API.

## 04:00

Simular timeout ambíguo com Fake Provider.

## 04:30

Executar reconciliation.

## 05:00

Mostrar ledger/audit.

---

# 9. Vídeo

Grave uma demo curta.

Não grave 45 minutos de terminal.

5–8 minutos.

Inclua capítulos.

---

# 10. Issues públicas

Use GitHub Issues como evidência de processo.

Labels:

```text
architecture
domain
datomic
provider
reliability
security
performance
good-first-issue
```

---

# 11. Pull Requests

Mesmo trabalhando sozinho, use PRs para features grandes.

PR deve conter:

```text
Context
Decision
Testing
Failure modes
Screenshots/traces when useful
```

---

# 12. Commit quality

Bom:

```text
feat: make inbound provider events idempotent
fix: preserve unknown outcome after Stripe timeout
test: prove concurrent refunds cannot exceed capture
docs: explain transaction-log relay trade-offs
```

Evite 300 commits:

```text
fix
fix2
agora vai
teste
```

---

# 13. Talking points para entrevista

## "Por que Datomic?"

Resposta baseada no projeto:

- system of record;
- immutable facts;
- audit;
- as-of/history;
- transaction metadata;
- serialized transactions;
- log.

Depois fale trade-offs, não evangelize.

## "Por que não PostgreSQL?"

Não responda "Datomic é melhor".

Explique que PostgreSQL também seria excelente para pagamentos, mas este projeto deliberadamente explora temporalidade/audit do Datomic e demonstra decisões específicas.

## "Por que não microsserviços?"

Explique custo de distribuição e evolução de fronteiras.

## "Como troca Stripe por Asaas?"

Mostre contract tests.

## "E se Stripe cobrar e sua conexão cair?"

Mostre Unknown Outcome + reconciliation.

## "E se webhook chegar duas vezes?"

Mostre inbox unique id.

## "Kafka exactly once?"

Explique que o projeto adota processamento idempotente e não vende garantia maior do que realmente possui.

---

# 14. Badges

Úteis:

```text
CI
Clojure
License
```

Não encha o README.

---

# 15. Benchmarks

Inclua ambiente.

Nunca:

```text
handles millions of payments
```

sem prova.

Prefira:

```text
On machine X with dataset Y:
p95 = ...
```

---

# 16. Security statement

O README deve deixar claro que:

- sandbox por padrão;
- secrets não inclusos;
- projeto de referência não é certificação PCI;
- não enviar cartão bruto.

Isso demonstra responsabilidade.

---

# 17. O que "sênior" aparece no repositório

Não é quantidade de código.

É a combinação:

```text
clear boundaries
trade-offs
failure handling
invariants
tests
observability
security
operational thinking
documentation
```

---

# 18. Checklist antes de divulgar

- README em inglês;
- documentação técnica pode ter versão PT-BR;
- GIF/video;
- one-command local run;
- CI verde;
- nenhuma secret;
- issues organizadas;
- arquitetura atualizada;
- ADRs;
- tests;
- performance doc;
- sample requests;
- Postman/Bruno/curl collection opcional;
- license;
- contributing guide opcional.

---

# 19. Descrição para currículo

Exemplo curto:

> Built a provider-agnostic payment orchestration service in Clojure and Datomic integrating Stripe and Asaas, with idempotent APIs/webhooks, temporal audit, reconciliation, double-entry ledger and event delivery via Kafka.

Não afirme uso em produção se não for verdade.

Se seu outro software realmente utilizar, você pode dizer que é utilizado por uma aplicação real, descrevendo escala de forma honesta.

---

# 20. Evolução como case study

Depois de integrar Asaas, escreva:

```text
docs/case-studies/adding-a-second-provider.md
```

Conte:

1. abstração original;
2. o que Stripe fez você assumir;
3. onde Asaas quebrou a suposição;
4. refactor;
5. versão final;
6. lições.

Esse documento pode ser um dos melhores itens do portfólio.
