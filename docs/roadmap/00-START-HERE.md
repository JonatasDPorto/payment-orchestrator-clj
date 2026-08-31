# Payment Orchestrator in Clojure — Start Here

> **Payment Orchestrator in Clojure** é uma plataforma de orquestração de pagamentos construída em Clojure + Datomic, desenhada para desacoplar aplicações de provedores como Stripe, Asaas, Mercado Pago, Adyen etc.

O objetivo do projeto é duplo:

1. ser um serviço de pagamentos realmente utilizável por outros softwares;
2. servir como um projeto de portfólio de nível sênior, demonstrando modelagem de domínio, Datomic, consistência, integrações externas, idempotência, sistemas distribuídos, observabilidade, segurança, testes e decisões arquiteturais.

---

## 1. A regra principal do projeto

Aplicações consumidoras **nunca devem depender de conceitos específicos da Stripe, Asaas ou qualquer outro provider**.

O consumidor conhece somente a API do Payment Orchestrator in Clojure:

```text
Seu Software
    |
    v
Payment Orchestrator in Clojure API
    |
    +---- Stripe
    |
    +---- Asaas
    |
    +---- Futuro Provider
```

Trocar:

```clojure
{:payments/default-provider :stripe}
```

por:

```clojure
{:payments/default-provider :asaas}
```

não deve exigir alterações no software consumidor para funcionalidades pertencentes ao contrato comum.

---

## 2. O que significa "provider agnostic"

O domínio NÃO deve conter:

```clojure
:stripe/payment-intent-id
:stripe/status
:asaas/payment-id
:asaas/event
```

O domínio DEVE conter conceitos próprios:

```clojure
:payment/id
:payment/status
:payment/amount
:payment/currency

:provider-payment/provider
:provider-payment/reference

:provider-event/provider
:provider-event/reference
```

Exemplo:

```clojure
{:payment/id #uuid "..."
 :payment/status :payment.status/paid
 :payment/amount 12990
 :payment/currency :BRL}
```

E separadamente:

```clojure
{:provider-payment/provider :stripe
 :provider-payment/reference "pi_123"}
```

O pagamento pertence ao Payment Orchestrator in Clojure. A Stripe é apenas um mecanismo utilizado para processá-lo.

---

## 3. Filosofia do roadmap

O roadmap foi construído com a seguinte regra:

> **Toda fase termina com algo testável, executável e demonstrável.**

Não construiremos Kafka antes de existir um pagamento.

Não construiremos Kubernetes antes de existir uma API.

Não construiremos reconciliação antes de existir uma integração real.

Não construiremos abstrações para dez providers antes de provar a arquitetura com dois.

Cada fase possui:

- objetivo;
- funcionalidades;
- passos de implementação;
- testes obrigatórios;
- demonstração manual;
- critérios de aceite;
- pontos de arquitetura para documentar;
- sugestão de commit/tag.

---

## 4. Ordem recomendada de leitura

1. `00-START-HERE.md`
2. `01-ROADMAP.md`
3. `02-ARCHITECTURE.md`
4. `03-DATOMIC-DATA-MODEL.md`
5. `04-PAYMENT-PROVIDER-CONTRACT.md`
6. `05-TESTING-STRATEGY.md`
7. `06-SECURITY-OPERATIONS.md`
8. `07-PORTFOLIO-INTERVIEW.md`
9. `08-DEFINITION-OF-DONE.md`

---

## 5. Stack sugerida

### Core

- Clojure
- Datomic Local no desenvolvimento
- Datomic Client API
- `deps.edn`
- Integrant para lifecycle/configuração
- Reitit para HTTP/routing
- Malli para contratos e validação

### Integrações

- Stripe como primeiro provider
- Asaas como segundo provider
- cliente HTTP explícito e isolado atrás dos adapters
- webhooks tratados via inbox persistente

### Mensageria

Introduzir apenas depois que o fluxo de pagamento estiver sólido:

- Kafka
- transaction-log relay baseado no Datomic

### Qualidade

- `clojure.test`
- `test.check`
- testes de contrato dos providers
- testes de concorrência
- testes de integração
- load testing em fase posterior

### Operação

- OpenTelemetry
- Prometheus
- logs estruturados
- Docker
- GitHub Actions
- Terraform/AWS somente depois do MVP funcional

---

## 6. Os marcos principais

| Marco | Resultado demonstrável |
|---|---|
| M0 | projeto Clojure sobe e testes rodam |
| M1 | domínio cria e transiciona pagamentos |
| M2 | pagamentos persistidos no Datomic |
| M3 | API HTTP independente de provider |
| M4 | idempotência na API |
| M5 | fake provider prova a arquitetura |
| M6 | Stripe Sandbox funcionando |
| M7 | webhooks Stripe idempotentes |
| M8 | Asaas Sandbox sem mudar o consumidor |
| M9 | ledger double-entry |
| M10 | auditoria temporal Datomic |
| M11 | retries + reconciliation |
| M12 | eventos internos e Kafka |
| M13 | observabilidade |
| M14 | segurança/hardening |
| M15 | performance e load tests |
| M16 | deploy e documentação de portfólio |

---

## 7. MVP utilizável

O primeiro MVP realmente útil para outro software termina em **M8**.

Nesse ponto deve ser possível:

```text
Seu Software
    |
    | POST /v1/payments
    v
Payment Orchestrator in Clojure
    |
    +---- Stripe
    |
    +---- Asaas
```

e alternar provider sem alterar o contrato da API do consumidor.

O restante do roadmap transforma o MVP em uma plataforma com características fortes de engenharia sênior.

---

## 8. Decisão deliberada: não começar com microsserviços

O projeto começa como **modular monolith**.

Motivos:

- domínio ainda está evoluindo;
- é mais fácil testar invariantes;
- transações são mais simples;
- observabilidade inicial é mais simples;
- evita complexidade distribuída artificial;
- permite descobrir fronteiras reais antes de extraí-las.

Kafka e processos separados serão introduzidos quando existirem problemas reais que justifiquem isso.

---

## 9. O que deve impressionar em entrevista

O projeto precisa permitir discutir:

- por que Datomic;
- bancos como valores;
- `as-of`;
- `history`;
- transaction metadata;
- idempotência;
- unicidade;
- consistência transacional;
- retries;
- webhooks at-least-once;
- inbox pattern;
- anti-corruption layer;
- ports and adapters;
- canonical payment model;
- capabilities de providers;
- reconciliação;
- double-entry ledger;
- dual-write problem;
- transaction log;
- Kafka;
- observabilidade;
- failure modes;
- property-based testing;
- segurança;
- trade-offs.

O objetivo não é usar palavras bonitas. É ter código, testes e ADRs que provem cada uma dessas decisões.

---

## 10. Regra de ouro durante o desenvolvimento

Antes de iniciar uma nova fase, execute:

```bash
clojure -M:test
```

e verifique:

```text
0 failures
0 errors
```

Depois execute a demo manual da fase anterior.

Somente então continue.

Cada etapa deve ser uma versão válida do produto.
