# Payment Orchestrator in Clojure

Provider-agnostic payment orchestration API built with Clojure and Datomic. It demonstrates safe payment boundaries: idempotency, provider isolation, durable webhook processing, temporal audit, double-entry accounting, reconciliation, Kafka relay, observability, and security hardening.

> Status: v1.0.0 release candidate. Stripe sandbox is supported; the Asaas milestone was intentionally skipped.

## Quick start with Docker

```powershell
Copy-Item .env.example .env
# Set PAYMENT_ORCHESTRATOR_API_KEY in .env to a private local value.
docker compose up --build
```

Create a payment in a second terminal, replacing the placeholder with the `.env` value:

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "X-Merchant-Id: demo-merchant" -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"card"}'
```

See [API.md](docs/API.md), [ARCHITECTURE.md](docs/ARCHITECTURE.md), [DEMO.md](docs/DEMO.md), [PERFORMANCE.md](docs/PERFORMANCE.md), and [SECURITY.md](SECURITY.md).

Payment Orchestrator in Clojure is an open-source provider-agnostic payment orchestration platform built with Clojure and Datomic. A API HTTP, Datomic Local, and the M5 Fake Provider are available for local validation.

## Pré-requisitos

- Java 21 ou mais recente
- [Clojure CLI](https://clojure.org/guides/install_clojure)

## Executar localmente

```bash
clojure -M -m payment-orchestrator-clj.core
```

O bootstrap registra o serviço e inicia Jetty na porta 8080.

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
docker run --rm -p 8080:8080 --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M -m payment-orchestrator-clj.core
```

Em outro terminal, defina `PAYMENT_ORCHESTRATOR_API_KEY` no `.env` e envie-a como Bearer token:

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-123","amount":12990,"currency":"BRL","method":"card"}'
```

## Idempotência da API (M4)

`POST /v1/payments` exige uma chave de idempotência. Repetir a mesma chave com o mesmo payload retorna o pagamento original; payload diferente retorna `409`.

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "Content-Type: application/json" -H "Idempotency-Key: demo-payment-001" -d '{"customerId":"cust-123","amount":12990,"currency":"BRL","method":"card"}'
```

## Payment Gateway Port (M5)

O gateway é definido pelo protocolo `PaymentGateway`; o ambiente padrão usa o Fake Provider determinístico (`:always-success`). O POST cria o pagamento local e retorna `processing`, persistindo a referência canônica do provider. Modos `:always-fail`, `:timeout` e `:requires-action` são cobertos pelos testes de contrato. Nenhuma instalação no host é necessária.

Valide contrato, fluxo e persistência exclusivamente via Docker:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test
docker run --rm -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-integration
```

## Stripe Sandbox Adapter (M6)

O Stripe é opcional e permanece isolado em `provider/stripe/`. Para executar o teste sandbox, use uma chave de teste e um payment method de teste somente no ambiente local; nunca grave esses valores em arquivos versionados.

Crie seu arquivo local a partir do exemplo:

```powershell
Copy-Item .env.example .env
```

Edite `.env` e informe sua `STRIPE_SECRET_KEY` de sandbox. O `.env` é ignorado pelo Git; `.env.example` não contém credenciais reais e é versionado.

```powershell
docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-stripe-sandbox
```

Para iniciar a API com Stripe, defina as mesmas variáveis e selecione o provider por ambiente (o padrão permanece Fake Provider):

```powershell
docker run --rm -p 8080:8080 --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M -m payment-orchestrator-clj.core
```

## Stripe Webhook Inbox (M7)

`POST /webhooks/stripe` valida o header `Stripe-Signature` sobre o corpo original antes de interpretar JSON. Eventos válidos entram em uma inbox Datomic idempotente e são processados fora da requisição. Apenas hash SHA-256 e campos operacionais são persistidos; o payload completo não é armazenado.

Para a demonstração local, inclua `STRIPE_WEBHOOK_SECRET` no `.env`, suba a API com o comando anterior e encaminhe eventos pelo Stripe CLI:

```powershell
stripe listen --forward-to http://localhost:8080/webhooks/stripe
```

Copie o segredo `whsec_...` exibido pelo CLI para `STRIPE_WEBHOOK_SECRET` e reinicie o container. Em seguida, crie um pagamento pela API M6: o Payment Intent criado pela aplicação será encaminhado pelo CLI e poderá ser associado ao pagamento local.

Para validar apenas a entrega do endpoint com um evento independente, use:

```powershell
stripe trigger payment_intent.succeeded
```

## Provider routing (M18)

Provider selection remains internal to the API. The pure routing policy supports default, merchant, currency, payment-method, availability, and lowest-cost choices from the configured gateway catalog. The public request never exposes provider-specific fields. A route whose chosen provider is unavailable fails safely; it is never retried through another provider, preventing an ambiguous operation from creating a duplicate charge. See [MULTI-TENANCY.md](docs/MULTI-TENANCY.md) for the local configuration contract.

## Pix (M19)

Pix is the first canonical payment capability that returns a customer action. The local Fake Provider and Stripe Pix adapter return a provider-neutral copy-and-paste payload, QR-code URL when supplied, hosted instructions URL when supplied, and expiry, with no raw Stripe object persisted. The transient Pix customer details required to create the provider payment method are never persisted or logged.

```powershell
curl.exe -X POST http://localhost:8080/v1/payments -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "X-Merchant-Id: demo-merchant" -H "Content-Type: application/json" -H "Idempotency-Key: pix-demo-001" -d '{"customerId":"cust-demo","amount":12990,"currency":"BRL","method":"pix","pix":{"taxId":"000.000.000-00","email":"succeed_immediately@example.com","name":"Pix Test"}}'
```

To validate the real Stripe sandbox adapter with a `sk_test_...` key, run:

```powershell
docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-stripe-pix-sandbox
```

The sandbox suite sends `payment_method_types[]=pix`, `currency=brl`, `confirm=true`, and the documented test CPF. It never claims success when Stripe reports that the account is ineligible; inspect the returned request ID in that case.

## Boleto (M20)

Boleto is a provider-neutral voucher action. Its generated number, hosted voucher URL, optional PDF URL, and expiry are persisted without retaining the billing details used to create it. Generating the voucher returns `requires-action`; settlement or expiry is applied only by the signed Stripe webhook.

```powershell
docker run --rm --env-file .env -v "${PWD}:/workspace" -w /workspace clojure:temurin-21-tools-deps clojure -M:test-stripe-boleto-sandbox
```

Stripe requires BRL and a minimum amount for Boleto; the sandbox suite uses `1000` minor units. Boleto payments cannot be refunded through Stripe.

## Subscriptions (M21)

Subscriptions, invoices, and payments are separate aggregates. A subscription stores the recurring agreement; an invoice records a single amount due and can reference the payment created to collect it. This milestone intentionally does not call a provider recurring-billing API or schedule collections automatically.

## Advanced refunds (M22)

Refunds are immutable canonical records. A payment may receive partial or
multiple refunds while their sum remains at or below the captured amount. The
payment becomes `partially-refunded` or `refunded` from that aggregate. Provider
refunds receive only the original provider payment reference and an amount; no
provider-specific object is returned by the public API.

```powershell
curl.exe -X POST "http://localhost:8080/v1/payments/<PAYMENT_ID>/refunds" -H "Authorization: Bearer <PAYMENT_ORCHESTRATOR_API_KEY>" -H "Content-Type: application/json" -d '{"amount":400}'
```

The response is `409 refund_amount_exceeds_captured` when the requested amount
would make the aggregate exceed the captured amount. Refund reconciliation
records provider/local amount mismatches for investigation without changing
financial history automatically.

## Disputes and chargebacks (M23)

Disputes are a separate bounded context linked to a payment ID, rather than a
new Payment status. Their provider reference is unique and their lifecycle is
tracked independently as `needs-response`, `under-review`, `won`, or `lost`.

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

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [API](docs/API.md)
- [Demo and recording checklist](docs/DEMO.md)
- [Performance profile](docs/PERFORMANCE.md)
- [Security policy](SECURITY.md)
- [Contributing](CONTRIBUTING.md)
- [v1.0.0 release notes](docs/RELEASE-NOTES-v1.0.0.md)
