# Payment Orchestrator in Clojure — Roadmap Incremental e Testável

Este é o documento principal de execução do projeto.

---

# Visão geral

O Payment Orchestrator in Clojure será construído em camadas de capacidade.

```text
M0  Fundação
 |
M1  Domínio
 |
M2  Datomic
 |
M3  HTTP API
 |
M4  Idempotência
 |
M5  Provider Contract + Fake
 |
M6  Stripe
 |
M7  Webhooks Stripe
 |
M8  Asaas
 |
M9  Ledger
 |
M10 Auditoria Temporal
 |
M11 Reconciliation
 |
M12 Events/Kafka
 |
M13 Observabilidade
 |
M14 Segurança
 |
M15 Performance
 |
M16 Deploy + Portfolio
```

Cada milestone deve gerar uma tag Git:

```text
v0.1.0-foundation
v0.2.0-domain
...
```

---

# M0 — Fundação do projeto

## Objetivo

Ter um projeto Clojure mínimo que:

- inicia;
- possui configuração;
- executa testes;
- possui lint/format opcional;
- possui estrutura de namespaces estável;
- possui CI.

Não existe pagamento ainda.

## Estrutura inicial

```text
payment-orchestrator-clj/
├── deps.edn
├── README.md
├── src/
│   └── payment_orchestrator_clj/
│       └── system.clj
├── test/
│   └── payment_orchestrator_clj/
│       └── system_test.clj
├── dev/
│   └── user.clj
├── resources/
│   └── config.edn
└── docs/
    └── adr/
```

## Passo a passo

### 1. Criar projeto

Defina aliases pelo menos para:

```clojure
:test
:dev
```

O objetivo não é possuir uma configuração perfeita. É conseguir executar:

```bash
clojure -M:test
```

### 2. Criar configuração

Use EDN:

```clojure
{:http {:port 8080}
 :database {:system "payment-orchestrator-clj"}}
```

Não coloque secrets reais no arquivo.

### 3. Criar lifecycle

Prepare o projeto para utilizar Integrant ou abordagem equivalente.

Nesta fase, um `system.clj` pode simplesmente validar configuração.

### 4. Criar primeiro teste

```clojure
(deftest system-can-load
  (is (= 1 1)))
```

O teste parece trivial, mas valida o pipeline.

### 5. Criar CI

Pipeline:

```text
checkout
 -> setup Java/Clojure
 -> restore cache
 -> run tests
```

## Testes obrigatórios

- suite local roda;
- suite CI roda;
- configuração inválida gera erro claro.

## Demo manual

```bash
git clone ...
cd payment-orchestrator-clj
clojure -M:test
```

Resultado esperado:

```text
0 failures, 0 errors
```

## Critério de aceite

Não avance enquanto uma pessoa que acabou de clonar o projeto não conseguir rodar os testes seguindo apenas o README.

## ADR sugerido

`ADR-0001-modular-monolith.md`

Explique por que o projeto começa como monólito modular.

## Commit sugerido

```text
chore: bootstrap Clojure project and test pipeline
```

---

# M1 — Domínio de pagamentos puro

## Objetivo

Construir o coração do Payment Orchestrator in Clojure sem:

- Datomic;
- HTTP;
- Stripe;
- Asaas;
- Kafka.

Tudo deve ser função pura sempre que possível.

## Conceitos

Criar:

```text
Payment
Money
PaymentStatus
PaymentMethod
PaymentTransition
```

## Estados iniciais

```text
created
processing
requires-action
authorized
paid
failed
cancelled
partially-refunded
refunded
```

Não permita transições arbitrárias.

Exemplo:

```text
created -> processing
processing -> paid
processing -> failed
paid -> partially-refunded
paid -> refunded
```

Mas:

```text
refunded -> processing
```

deve ser inválido.

## Passo a passo

### 1. Criar `payment/domain.clj`

Defina constructors que retornam mapas comuns.

Exemplo conceitual:

```clojure
(defn new-payment
  [{:keys [id customer-id amount currency method]}]
  {:payment/id id
   :payment/customer-id customer-id
   :payment/amount amount
   :payment/currency currency
   :payment/method method
   :payment/status :payment.status/created})
```

### 2. Dinheiro

Internamente, prefira valor inteiro em unidade mínima:

```text
R$ 129,90 -> 12990
```

Evite double/float.

Crie validações:

- amount > 0;
- currency válida;
- integer.

### 3. State machine

Implemente:

```clojure
(transition payment :payment.status/processing)
```

e rejeite transições inválidas com dados explicativos.

### 4. Eventos de domínio

Uma transição pode produzir:

```clojure
{:event/type :payment/created
 :event/payment-id ...
 :event/occurred-at ...}
```

Não publique em lugar algum ainda.

## Testes obrigatórios

### Unitários

- criação válida;
- amount zero rejeitado;
- amount negativo rejeitado;
- moeda inválida rejeitada;
- transições permitidas;
- transições proibidas.

### Property tests

Propriedade:

> Todo pagamento criado possui amount positivo e status `created`.

Propriedade:

> Nenhuma sequência de transições permitidas retorna de `refunded` para `processing`.

## Demo manual

Abra REPL:

```clojure
(def p (payment/new-payment {...}))
(payment/transition p :payment.status/processing)
(payment/transition ... :payment.status/paid)
```

Tente uma transição inválida e mostre o erro.

## Critério de aceite

O domínio inteiro roda sem nenhuma dependência de infraestrutura.

## ADR

`ADR-0002-canonical-payment-model.md`

## Commit

```text
feat: add provider-agnostic payment domain
```

---

# M2 — Persistência no Datomic

## Objetivo

Persistir o domínio criado em M1 sem contaminar o domínio com detalhes Datomic.

## Namespaces

```text
datomic/
  client.clj
  schema.clj

payment/
  repository.clj
  datomic_repository.clj
```

## Modelo inicial

Payment:

```text
:payment/id                  uuid unique identity
:payment/customer-id         uuid/string
:payment/amount              long
:payment/currency            keyword
:payment/method              keyword
:payment/status              keyword
:payment/created-at          instant
```

## Passo a passo

### 1. Subir Datomic Local

Crie configuração separada para teste:

```clojure
{:database
 {:server-type :datomic-local
  :system "payment-orchestrator-clj-test"}}
```

### 2. Schema como dados

Mantenha schema versionado no repositório.

### 3. Repository boundary

Não faça chamadas `d/q` espalhadas por handlers.

Centralize acesso.

Exemplos de operações:

```clojure
(save-payment! repo payment)
(find-payment repo id)
```

### 4. Mapear dados

Implemente explicitamente:

```text
domain -> datomic transaction data
datomic pull -> domain
```

Não esconda o Datomic atrás de uma abstração genérica de ORM.

### 5. Transaction metadata

Desde agora grave:

```text
:tx/request-id
:tx/correlation-id
:tx/source
```

quando aplicável.

## Testes

### Integration

Para cada teste:

1. criar database isolada;
2. transacionar schema;
3. executar cenário;
4. destruir database.

Teste:

```text
save -> retrieve -> same business value
```

Teste uniqueness:

```text
mesmo payment/id não gera duas entidades
```

## Demo

REPL:

```clojure
(save-payment! ...)
(find-payment ...)
```

Depois query Datalog mostrando o registro.

## Critério de aceite

Nenhum teste unitário do domínio depende do Datomic.

Os testes de integração Datomic são separados.

## ADR

`ADR-0003-why-datomic.md`

Explique:

- data-of-record;
- imutabilidade;
- temporalidade;
- auditoria;
- trade-offs.

## Commit

```text
feat: persist payments with Datomic
```

---

# M3 — API HTTP v1

## Objetivo

Permitir que um software externo crie e consulte pagamentos.

Ainda sem provider real.

## Endpoints

```http
POST /v1/payments
GET  /v1/payments/:id
```

## Request

```json
{
  "customerId": "cust-123",
  "amount": 12990,
  "currency": "BRL",
  "method": "card"
}
```

## Response

```json
{
  "id": "...",
  "status": "created",
  "amount": 12990,
  "currency": "BRL"
}
```

## Princípio

A API pública NÃO deve revelar:

```text
datomic-id
stripe-id
asaas-id
```

## Passo a passo

### 1. Criar schemas Malli

Separar:

```text
HTTP DTO
Domain
```

DTOs podem usar strings.

Domínio pode usar keywords.

### 2. Handler fino

Handler deve:

```text
parse
validate
call application service
map response
```

Não deve conter regra de negócio.

### 3. Application service

Criar:

```clojure
(create-payment! deps command)
```

Fluxo:

```text
request
 -> validation
 -> domain
 -> repository
 -> response
```

### 4. Error model

Padronize:

```json
{
  "error": {
    "code": "invalid_payment",
    "message": "...",
    "details": {}
  }
}
```

## Testes

- POST válido -> 201;
- GET existente -> 200;
- GET inexistente -> 404;
- request inválido -> 400;
- amount negativo -> 400;
- JSON inválido -> 400.

## Demo

```bash
curl -X POST localhost:8080/v1/payments ...
curl localhost:8080/v1/payments/<id>
```

## Critério de aceite

Outro projeto consegue consumir a API sem conhecer Clojure/Datomic.

## Commit

```text
feat: expose provider-agnostic payment API
```

---

# M4 — Idempotência da API

## Objetivo

Proteger criação de pagamentos contra retries do consumidor.

## API

```http
POST /v1/payments
Idempotency-Key: 5b31...
```

## Comportamento

Primeiro POST:

```text
201 payment A
```

Segundo POST com mesma key e mesmo payload:

```text
200/201 payment A
```

Nunca payment B.

Mesmo key com payload diferente:

```text
409 idempotency_conflict
```

## Modelo

```text
:idempotency/key
:idempotency/request-hash
:idempotency/payment
:idempotency/created-at
```

A key deve ter unicidade.

## Passo a passo

1. normalizar o comando;
2. calcular hash determinístico;
3. procurar key;
4. se inexistente, executar operação;
5. persistir key + resultado de forma transacional;
6. se existente e hash igual, retornar resultado anterior;
7. se existente e hash diferente, erro.

## Testes

### Sequencial

```text
100 chamadas com mesma key -> 1 payment
```

### Concorrência

Dispare simultaneamente várias chamadas com a mesma key.

Critério:

```text
count(payment created for key) == 1
```

### Conflict

Mesmo key:

```json
{"amount": 1000}
```

depois:

```json
{"amount": 2000}
```

deve falhar.

## Demo

Execute o mesmo curl duas vezes.

Depois query Datomic mostrando uma única entidade Payment.

## Por que esta fase importa

Aqui começa a discussão real de pagamentos e sistemas distribuídos.

## ADR

`ADR-0004-api-idempotency.md`

## Commit

```text
feat: guarantee idempotent payment creation
```

---

# M5 — Payment Provider Port + Fake Provider

## Objetivo

Criar a fronteira que permitirá integrar Stripe e Asaas sem acoplar o domínio.

Não conecte Stripe ainda.

## Contrato conceitual

```clojure
(defprotocol PaymentGateway
  (capabilities [gateway])
  (create-payment! [gateway command])
  (fetch-payment [gateway reference])
  (cancel-payment! [gateway command])
  (refund-payment! [gateway command]))
```

Os resultados DEVEM ser canônicos.

Exemplo:

```clojure
{:provider-payment/reference "fake-123"
 :provider-payment/status :provider.status/processing
 :provider-payment/raw-status "PROCESSING"}
```

## Fake Provider

Implemente provider determinístico.

Ele deve permitir configurar cenários:

```clojure
{:mode :always-success}
{:mode :always-fail}
{:mode :timeout}
{:mode :requires-action}
```

## Por que Fake antes de Stripe

Porque você testa a arquitetura sem misturar bugs arquiteturais com detalhes de API externa.

## Passo a passo

### 1. Criar port

Namespace:

```text
provider/port.clj
```

### 2. Criar resultado canônico

Não retorne payload externo cru para application layer.

### 3. Criar fake adapter

```text
provider/fake.clj
```

### 4. Alterar `create-payment!`

Novo fluxo:

```text
create local payment
 -> select provider
 -> provider/create
 -> persist provider reference
 -> transition local status
```

### 5. Capabilities

```clojure
#{:payment/create
  :payment/refund
  :payment/cancel}
```

## Testes

### Contract suite

Escreva uma função de teste reutilizável:

```clojure
(payment-provider-contract gateway)
```

Todo provider futuro deve passar pela mesma suite.

Teste:

- cria;
- consulta;
- refund quando suportado;
- erro canônico;
- reference nunca é nil;
- status sempre pertence ao conjunto canônico.

## Demo

Configure:

```clojure
:provider :fake
```

Crie pagamento pela API.

Simule failure.

## Critério de aceite

É possível trocar uma implementação fake por outra sem mudar o handler HTTP nem `payment/domain.clj`.

## ADR

`ADR-0005-provider-port.md`

## Commit

```text
feat: introduce payment gateway port and fake adapter
```

---

# M6 — Stripe Sandbox Adapter

## Objetivo

Processar o primeiro pagamento real em ambiente de teste.

## Regra

Stripe aparece SOMENTE dentro de:

```text
provider/stripe/
```

## Estrutura

```text
provider/stripe/
  client.clj
  adapter.clj
  mapper.clj
  errors.clj
```

## Responsabilidades

### client.clj

HTTP/SDK, autenticação, timeout.

### mapper.clj

```text
Payment Orchestrator in Clojure -> Stripe
Stripe -> Canonical Provider Result
```

### adapter.clj

implementa `PaymentGateway`.

### errors.clj

traduz erros externos.

## Idempotência outbound

Toda chamada Stripe que cria/muta recursos deve utilizar uma idempotency key derivada da operação interna.

Exemplo conceitual:

```text
payment-orchestrator-clj:create-payment:<operation-id>
```

Nunca reuse a mesma key para operações semanticamente diferentes.

## Passo a passo

1. criar conta/sandbox Stripe;
2. carregar secret por environment variable;
3. implementar client;
4. definir timeouts;
5. criar mapper;
6. implementar adapter;
7. rodar contract tests;
8. criar integration tests marcados;
9. testar via Payment Orchestrator in Clojure API.

## Testes

### Sem rede

Mock/stub apenas no nível do client.

Teste mapper com fixtures.

### Contract

Stripe adapter deve passar a suite de M5.

### Sandbox

Teste marcado:

```text
^:integration
```

e não obrigatório para todo unit test local.

## Cenários

- sucesso;
- card requires action, se aplicável;
- declined;
- timeout;
- provider 4xx;
- provider 5xx.

## Demo

```text
POST Payment Orchestrator in Clojure
 -> Stripe sandbox
 -> response canonical
```

Mostre que a resposta da API não contém nomes de tipos da Stripe.

## Critério de aceite

Não existe `"payment_intent.succeeded"` fora do módulo Stripe/webhook.

## ADR

`ADR-0006-stripe-adapter-boundary.md`

## Commit

```text
feat: add Stripe sandbox payment adapter
```

---

# M7 — Stripe Webhook Inbox

## Objetivo

Processar mudanças assíncronas de status de forma segura.

## Arquitetura

```text
Stripe
 |
 v
/webhooks/stripe
 |
verify signature
 |
persist inbox event
 |
return success
 |
async processor
 |
canonical event
 |
domain transition
```

## Regra crítica

Persistir antes de executar regras demoradas.

## Modelo

```text
:provider-event/id
:provider-event/provider
:provider-event/external-id
:provider-event/type
:provider-event/payload
:provider-event/status
:provider-event/received-at
:provider-event/processed-at
```

`provider + external-id` deve impedir duplicação.

## Stripe signature

A verificação de assinatura precisa receber o corpo HTTP original, antes de transformações que alterem bytes.

## Passo a passo

1. endpoint com raw body;
2. validar assinatura;
3. parse apenas após validação;
4. mapear id/type;
5. persistir inbox;
6. responder rapidamente;
7. processor busca `pending`;
8. mapper converte evento externo em evento canônico;
9. application service aplica transição;
10. marcar `processed`.

## Testes

- assinatura inválida -> 400;
- mesmo event entregue duas vezes -> 1 processamento;
- evento desconhecido -> persistido/ignorado de forma segura;
- processor falha -> permanece recuperável;
- restart -> pending é reprocessável;
- pagamento inexistente -> estado de erro observável.

## Demo

Use ferramenta oficial/sandbox para emitir webhook.

Depois mostre:

```text
provider event
 -> Datomic inbox
 -> payment status
```

## Critério de aceite

Duplicar exatamente o mesmo webhook não pode gerar duas transições de negócio.

## ADR

`ADR-0007-webhook-inbox.md`

## Commit

```text
feat: process Stripe webhooks idempotently
```

---

# M8 — Asaas Adapter: o teste real da arquitetura

## Objetivo

Adicionar um segundo provider sem alterar o contrato externo do Payment Orchestrator in Clojure.

Esta é uma das fases mais importantes do projeto.

## Regra de sucesso arquitetural

Ao implementar Asaas, alterações significativas em:

```text
payment/domain.clj
api/
provider/port.clj
```

devem ser exceção e exigir explicação em ADR.

A maior parte deve ser simplesmente:

```text
provider/asaas/
```

## Estrutura

```text
provider/asaas/
  client.clj
  adapter.clj
  mapper.clj
  webhook.clj
  errors.clj
```

## Webhook

Asaas possui entrega at-least-once, portanto trate `id` como identificador idempotente do evento.

Fluxo:

```text
receive
 -> validate token
 -> persist event id
 -> HTTP 200
 -> process async
```

## Capabilities

Agora prove por que existe:

```clojure
(capabilities gateway)
```

Exemplo ilustrativo:

```clojure
:stripe #{:card :refund}
:asaas  #{:card :pix :boleto :refund}
```

As capabilities reais devem refletir somente funcionalidades implementadas pelo Payment Orchestrator in Clojure, não tudo o que o provedor oferece.

## Testes

Rode exatamente a contract suite de M5 contra:

```text
Fake
Stripe
Asaas
```

Crie teste fundamental:

```text
Consumer Contract Test
```

O mesmo request Payment Orchestrator in Clojure:

```json
{
  "amount": 12990,
  "currency": "BRL",
  "method": "card"
}
```

deve produzir o mesmo formato de resposta independentemente do provider.

## Teste de substituição

Execute:

```clojure
{:default-provider :stripe}
```

suite.

Depois:

```clojure
{:default-provider :asaas}
```

suite.

Nenhum teste do consumidor deve mudar.

## Demo de portfólio

Essa é uma demo excelente:

1. subir Payment Orchestrator in Clojure com Stripe;
2. criar pagamento;
3. mudar config;
4. reiniciar com Asaas;
5. executar exatamente o mesmo `curl`;
6. mostrar formato idêntico.

## Critério de aceite

O consumidor não conhece a troca.

## ADR

`ADR-0008-second-provider-validation.md`

Documente quais abstrações sobreviveram e quais precisaram mudar.

## Commit

```text
feat: add Asaas provider without changing consumer API
```

---

# M9 — Double-entry Ledger

## Objetivo

Separar "estado do provider" de "registro financeiro interno".

Não utilize o saldo do provider como fonte única de verdade contábil.

## Conceitos

```text
LedgerAccount
JournalTransaction
Posting
Debit
Credit
```

## Invariante

Para toda journal transaction:

```text
sum(debits) == sum(credits)
```

## Modelo simplificado

Pagamento R$ 100:

```text
Dr processor receivable 100
Cr merchant payable    100
```

Taxa R$ 3:

```text
Dr provider fee expense 3
Cr processor receivable 3
```

O modelo final depende do produto, mas a invariância deve existir desde o começo.

## Passo a passo

1. domínio ledger puro;
2. accounts;
3. journal;
4. postings;
5. validação balanceada;
6. persistência Datomic;
7. associar payment;
8. criar lançamentos somente em transições definidas.

## Testes

### Unit

- journal balanceado aceita;
- journal desbalanceado rejeita.

### Property based

Gere journals válidos e prove:

```text
Σ debit == Σ credit
```

### Idempotência

Processar duas vezes o mesmo evento `payment.paid` não gera journal duplicado.

## Demo

Consultar:

```http
GET /v1/payments/:id/ledger
```

e mostrar lançamentos.

## Critério de aceite

Nenhuma alteração financeira é feita simplesmente mutando `:account/balance`.

## ADR

`ADR-0009-double-entry-ledger.md`

## Commit

```text
feat: add immutable double-entry ledger
```

---

# M10 — Auditoria temporal com Datomic

## Objetivo

Fazer Datomic ser uma parte essencial do produto, não só um banco diferente.

## Features

```http
GET /v1/payments/:id/history
GET /v1/payments/:id?asOf=<time-or-t>
```

## Use

- `d/history`;
- `d/as-of`;
- transaction metadata;
- transaction ids/t quando precisão importa.

## Transaction metadata

Amplie:

```text
:tx/request-id
:tx/correlation-id
:tx/actor
:tx/source
:tx/reason
:tx/event-type
```

## Histórico

Mostre:

```text
12:00 created
12:00 processing
12:01 paid
12:05 partially-refunded
```

## Passo a passo

1. query current;
2. query history database;
3. join com `:db/txInstant`;
4. mapear datoms em timeline;
5. endpoint de audit;
6. endpoint as-of;
7. ocultar detalhes internos sensíveis.

## Testes

- estado current = último estado;
- `as-of` antes da transição mostra estado anterior;
- history contém assertion/retraction esperadas;
- timeline ordenada;
- metadata aponta request responsável.

## Demo

1. criar payment;
2. alterar status;
3. consultar atual;
4. consultar estado anterior;
5. mostrar histórico.

## Critério de aceite

Você consegue responder:

> Qual era o status desse pagamento quando a transação T foi processada?

## ADR

`ADR-0010-temporal-audit.md`

## Commit

```text
feat: expose payment audit using Datomic
```

---

# M11 — Retry, Unknown Outcome e Reconciliation

## Objetivo

Tratar o problema:

```text
Payment Orchestrator in Clojure -> Provider
             |
             X timeout
```

Timeout NÃO significa automaticamente "provider não processou".

## Estados operacionais

Considere representar:

```text
processing
provider-outcome-unknown
reconciliation-required
```

sem confundir estado técnico com estado financeiro.

## Reconciliation Worker

```text
Datomic
 |
query unresolved
 |
v
Reconciliation Worker
 |
provider/fetch
 |
compare
 |
correct / alert
```

## Passo a passo

### 1. Classificar erros

```text
definitive failure
retryable failure
unknown outcome
```

### 2. Retry policy

Retry apenas quando seguro.

Backoff + jitter.

### 3. Provider operation id

Toda operação externa precisa ter identidade interna estável.

### 4. Reconciliation

Busque pagamentos:

```text
processing > threshold
unknown outcome
webhook overdue
```

Pergunte ao provider pelo estado.

### 5. Registrar decisão

Toda reconciliation cria audit metadata.

## Testes

- timeout depois de provider aceitar;
- timeout antes do provider aceitar;
- webhook perdido;
- webhook atrasado;
- duplicate reconciliation;
- provider indisponível;
- provider diverge do local.

## Teste com Fake Provider

O fake deve conseguir simular:

```text
commit-then-timeout
```

Este cenário é excelente para provar design.

## Demo

1. configurar fake em `commit-then-timeout`;
2. criar payment;
3. estado local vira unknown/reconciliation;
4. executar worker;
5. worker descobre remote success;
6. payment vira paid;
7. audit mostra correção.

## Critério de aceite

O sistema nunca faz fallback financeiro automático apenas porque ocorreu timeout.

## ADR

`ADR-0011-unknown-payment-outcomes.md`

## Commit

```text
feat: reconcile ambiguous provider outcomes safely
```

---

# M12 — Event Relay + Kafka

## Objetivo

Publicar eventos para outros sistemas sem introduzir dual-write ingênuo.

Evite:

```text
transact Datomic
then
publish Kafka
```

como única garantia.

## Arquitetura

```text
Datomic transaction
       |
       v
transaction log
       |
       v
relay
       |
       v
Kafka
```

## Eventos públicos

Exemplos:

```text
payment.created
payment.processing
payment.paid
payment.failed
payment.refunded
```

Payload versionado:

```json
{
  "eventId": "...",
  "type": "payment.paid",
  "version": 1,
  "aggregateId": "...",
  "occurredAt": "...",
  "data": {}
}
```

## Passo a passo

1. marcar transações relevantes;
2. consumir transaction log com range;
3. criar checkpoint;
4. mapear tx -> event;
5. publicar Kafka;
6. persistir checkpoint apenas com estratégia segura;
7. assumir at-least-once;
8. consumidores devem ser idempotentes.

## Testes

- relay restart;
- duplicate publish;
- Kafka unavailable;
- checkpoint antigo;
- poison event;
- consumer duplicado;
- ordering por payment quando necessário.

## Demo

```text
POST payment
 -> Datomic
 -> relay
 -> Kafka
 -> demo consumer prints payment.created
```

## Critério de aceite

Crash depois do commit Datomic não perde permanentemente o evento.

## ADR

`ADR-0012-datomic-log-event-relay.md`

## Commit

```text
feat: publish durable domain events from Datomic log
```

---

# M13 — Observabilidade

## Objetivo

Responder rapidamente:

```text
o que quebrou?
onde?
desde quando?
qual pagamento foi afetado?
```

## Logs estruturados

Campos mínimos:

```text
timestamp
level
service
request_id
correlation_id
payment_id
provider
operation
error_code
duration_ms
```

Nunca logar secrets ou dados completos de cartão.

## Métricas

```text
http_request_duration_seconds
payment_create_total
payment_status_transition_total
provider_request_duration_seconds
provider_request_errors_total
webhook_received_total
webhook_duplicate_total
webhook_processing_lag_seconds
reconciliation_total
reconciliation_mismatch_total
event_relay_lag
ledger_invariant_failure_total
```

## Tracing

Span tree:

```text
HTTP POST /payments
  payment.create
    datomic.transact
    provider.create
```

Webhook:

```text
webhook.receive
  webhook.persist
  payment.apply-provider-event
  datomic.transact
```

## Testes

- correlation id propagado;
- logs não contêm secret;
- métrica incrementa;
- provider timeout aparece como categoria própria;
- trace contém provider span.

## Demo

Crie um dashboard mínimo.

Mostre um pagamento do request inicial ao webhook.

## Critério de aceite

É possível localizar uma operação por `payment_id` ou `correlation_id`.

## Commit

```text
feat: add end-to-end payment observability
```

---

# M14 — Segurança e hardening

## Objetivo

Transformar "funciona" em "pode ser operado responsavelmente".

## Áreas

- secret management;
- webhook authenticity;
- authentication da API;
- authorization;
- rate limiting;
- PII;
- logs;
- dependency scanning;
- threat modeling.

## Princípio PCI

Evite receber PAN/cartão cru quando o provider oferece tokenização/hosted components.

Payment Orchestrator in Clojure deve trabalhar preferencialmente com referências/tokens gerados de forma apropriada pelo provider.

## API auth

Comece simples e explícito:

```text
service API key
```

Depois avalie OAuth/mTLS conforme necessidade real.

## Webhooks

Stripe:

```text
signature verification
raw request body
timestamp tolerance
```

Asaas:

```text
auth token header
constant-time comparison quando aplicável
```

## Testes

- missing auth;
- invalid auth;
- webhook forged;
- replay;
- oversized body;
- malformed JSON;
- secret não aparece em exceptions;
- rate limit;
- user não acessa merchant errado, se multi-tenant.

## Threat model

Documente STRIDE simplificado.

## Critério de aceite

Existe uma seção SECURITY.md dizendo:

- o que o sistema protege;
- o que NÃO protege;
- como reportar vulnerabilidade;
- quais dados não devem passar pela API.

## Commit

```text
security: harden payment API and provider webhooks
```

---

# M15 — Performance e resiliência

## Objetivo

Ter números reais e documentados.

## Não invente benchmark

Crie dataset e execute.

## Workloads

### Read

```text
GET /payments/:id
```

### Write

```text
POST /payments
```

com Fake Provider para medir aplicação, e separado com provider sandbox.

### History

```text
GET /payments/:id/history
```

### Webhook burst

Centenas/milhares de eventos fake.

## Métricas

```text
throughput
p50
p95
p99
error rate
CPU
memory
GC
Datomic transaction latency
```

## Resilience tests

- provider 5s latency;
- provider timeout;
- Kafka down;
- webhook duplicate storm;
- consumer restart;
- Datomic temporary failure.

## Documente

Para cada otimização:

```text
Observation
Hypothesis
Experiment
Result
Decision
```

## Critério de aceite

`docs/PERFORMANCE.md` possui números reproduzíveis e máquina/ambiente descritos.

## Commit

```text
perf: document load profile and resilience limits
```

---

# M16 — Deploy, documentação e apresentação de portfólio

## Objetivo

Fazer alguém avaliar o projeto em menos de 10 minutos e perceber profundidade.

## Docker

Um comando ideal:

```bash
docker compose up
```

deve iniciar o máximo possível do ambiente local.

## Cloud

Depois do local estabilizado:

- Terraform;
- AWS;
- secrets manager;
- observabilidade;
- CI/CD.

Não adicione cloud apenas como decoração.

## README final

A primeira tela deve responder:

1. o que é;
2. por que existe;
3. arquitetura;
4. como rodar;
5. quais problemas difíceis resolve.

## Demo script

### Cena 1 — Provider agnostic

Criar pagamento com Stripe.

Mudar config.

Criar o mesmo pagamento com Asaas.

### Cena 2 — Idempotência

Enviar request duas vezes.

Mostrar um payment.

### Cena 3 — Webhook duplicate

Enviar duas vezes.

Mostrar um processamento.

### Cena 4 — Datomic time travel

Mostrar payment atual.

Depois estado antigo via `as-of`.

### Cena 5 — Unknown outcome

Simular timeout ambíguo.

Rodar reconciliation.

### Cena 6 — Ledger

Mostrar postings balanceados.

### Cena 7 — Events

Mostrar Kafka consumer.

## Critério de aceite

Um recrutador consegue:

```text
clone
run
execute demo
read architecture
```

sem falar com o autor.

## Tag

```text
v1.0.0
```

---

# Milestones opcionais pós-v1

Somente depois de v1.

---

## M17 — Multi-tenant / Merchants

Conceitos:

```text
Merchant
ProviderAccount
MerchantProviderConfiguration
```

Permitir:

```text
Merchant A -> Stripe
Merchant B -> Asaas
```

ou roteamento por payment.

### Testes

Nenhum merchant pode acessar payment de outro.

---

## M18 — Routing Strategy

Provider selection:

```text
default
by currency
by payment method
by merchant
by availability
by cost
```

NÃO faça fallback cego quando outcome for desconhecido.

### Teste

Routing é função pura:

```clojure
(select-provider context providers)
```

---

## M19 — PIX

Adicionar uma capability real que difere entre providers.

Canonical model pode conter:

```text
:payment.action/type :pix/qr-code
:payment.action/expires-at
```

Evite vazar payload inteiro do Asaas.

---

## M20 — Boleto

Mesmo princípio:

```text
payment method capability
canonical action/result
```

---

## M21 — Subscriptions

Não force assinatura dentro do mesmo aggregate `Payment`.

Considere:

```text
Subscription
Invoice
Payment
```

Payment é consequência de cobrança, não necessariamente a assinatura em si.

---

## M22 — Refunds avançados

Suportar:

```text
partial refund
multiple refunds
refund reconciliation
```

Invariante:

```text
sum(refunds) <= captured amount
```

Property test obrigatório.

---

## M23 — Disputes/Chargebacks

Novo bounded context.

Não misture imediatamente em Payment.

---

## M24 — Webhook delivery para consumidores do Payment Orchestrator in Clojure

Payment Orchestrator in Clojure passa a emitir webhook próprio:

```text
Payment Orchestrator in Clojure
  |
  v
consumer webhook
```

Implementar:

- signing;
- retry;
- dead letter;
- delivery logs;
- event idempotency.

Isto transforma Payment Orchestrator in Clojure em uma plataforma completa.

---

# Ordem de prioridade para uso real

Se você já possui um software que precisa de pagamento, priorize:

```text
M0
M1
M2
M3
M4
M5
M6
M7
M8
```

Nesse ponto conecte seu software real.

Depois:

```text
M9
M10
M11
```

e só então:

```text
M12+
```

Assim o projeto entrega valor cedo.

---

# Regra para cada Pull Request

Todo PR deve responder:

```text
Qual problema resolve?
Qual comportamento muda?
Qual teste prova isso?
Qual failure mode foi considerado?
Existe mudança de contrato?
Existe decisão arquitetural relevante?
```

Se não houver teste que prove a funcionalidade, o PR não está pronto.

---

# Regra para abstrações

Não crie abstração porque "pode precisar".

Crie quando existirem ao menos dois casos concretos ou quando a fronteira de dependência for evidente.

Exemplo bom:

```text
PaymentGateway
```

Existe para impedir o domínio de depender de Stripe.

Exemplo ruim:

```text
AbstractGenericExternalFinancialResourceManagerFactory
```

antes de existir qualquer necessidade.

---

# Regra para failure modes

Para toda integração externa, documente:

```text
success
explicit client error
explicit provider error
timeout before request
timeout after unknown processing
connection reset
duplicate request
duplicate webhook
out-of-order webhook
provider unavailable
malformed provider response
```

Pagamentos ficam interessantes exatamente nesses caminhos.

---

# Resultado final esperado

Ao concluir o roadmap, o Payment Orchestrator in Clojure deve ser capaz de demonstrar:

```text
Application
   |
   v
Stable Payment Orchestrator in Clojure API
   |
   +--> canonical payment domain
   |
   +--> Datomic system of record
   |
   +--> provider adapter
   |      + Stripe
   |      + Asaas
   |
   +--> idempotent webhook inbox
   |
   +--> reconciliation
   |
   +--> double-entry ledger
   |
   +--> transaction log relay
   |
   +--> Kafka
   |
   +--> observability
```

E, mais importante, cada bloco existirá porque um problema real exigiu sua existência.
