# Payment Orchestrator in Clojure

Payment Orchestrator in Clojure is an open-source provider-agnostic payment orchestration platform built with Clojure and Datomic. A API HTTP, Datomic Local, and the M5 Fake Provider are available for local validation.

## Pré-requisitos

- Java 21 ou mais recente
- [Clojure CLI](https://clojure.org/guides/install_clojure)

## Executar localmente

```bash
clojure -M -m payment-orchestrator-clj.core
```

Saída esperada:

```text
Payment Orchestrator in Clojure bootstrap ready: #:payment-orchestrator-clj{:service-name payment-orchestrator-clj, :environment :development}
```

## Testes

```bash
clojure -M:test
```

Sem instalar o Clojure CLI no host, execute a mesma suíte com Docker:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test
```

Os testes de integração do Datomic são separados:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration
```

## Logging local

O projeto usa `slf4j-simple` com timestamp, thread, logger e nível `INFO`. O bootstrap registra apenas nome do serviço e ambiente; não registra payloads, identificadores de cliente, tokens ou secrets. A configuração está em `resources/simplelogger.properties`.

## API HTTP (M3)

Inicie a API em `http://localhost:8080`:

```powershell
docker run --rm -p 8080:8080 -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M -m payment-orchestrator-clj.core
```

Em outro terminal:

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-123","amount":12990,"currency":"BRL","method":"card"}'
```

## Idempotência da API (M4)

`POST /v1/payments` exige uma chave de idempotência. Repetir a mesma chave com o mesmo payload retorna o pagamento original; payload diferente retorna `409`.

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-123","amount":12990,"currency":"BRL","method":"card"}'
```

## Payment Gateway Port (M5)

O gateway é definido pelo protocolo `PaymentGateway`; o ambiente padrão usa o Fake Provider determinístico (`:always-success`). O POST cria o pagamento local e retorna `processing`, persistindo a referência canônica do provider. Modos `:always-fail`, `:timeout` e `:requires-action` são cobertos pelos testes de contrato. Nenhuma instalação no host é necessária.

Valide contrato, fluxo e persistência exclusivamente via Docker:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration
```

## REPL de desenvolvimento

```bash
clojure -M:dev
```

## Domínio de pagamentos (M1)

O domínio é independente de banco, HTTP e providers. Valores monetários usam a menor unidade inteira: `12990` representa R$ 129,90.

No REPL:

```clojure
(require '[payment-orchestrator-clj.payment.domain :as payment])

(def p (payment/new-payment {:id #uuid "2ee9a79d-8ccf-4c75-89a2-beb89b271ca1"
                             :customer-id "customer-123"
                             :amount 12990
                             :currency :BRL
                             :method :payment.method/card}))
(payment/transition p :payment.status/processing)
```

## Persistência Datomic (M2)

O repositório Datomic recebe e devolve mapas do domínio; a Client API fica confinada à infraestrutura. Em testes, Datomic Local usa bancos em memória isolados. O schema inicial e sua primeira versão estão em `src/payment_orchestrator_clj/datomic/schema/`.

Rode a suíte de integração separada:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration
```

## Roadmap

Leia [00-START-HERE.md](docs/roadmap/00-START-HERE.md) e [01-ROADMAP.md](docs/roadmap/01-ROADMAP.md) antes de iniciar uma etapa. A implementação segue um milestone por vez; o próximo só começa após validação do anterior.
