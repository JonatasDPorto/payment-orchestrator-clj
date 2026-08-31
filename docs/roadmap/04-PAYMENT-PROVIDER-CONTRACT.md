# Payment Orchestrator in Clojure — Contrato de Payment Providers

Este documento define a fronteira mais importante do projeto.

Se esta fronteira for boa, trocar Stripe por Asaas não afeta consumidores.

---

# 1. Objetivo

Providers são plugins de infraestrutura.

```text
Payment Domain
      |
PaymentGateway Port
      |
  +---+----+
  |        |
Stripe   Asaas
```

---

# 2. O contrato deve ser pequeno

Não copie toda API da Stripe.

Não copie toda API do Asaas.

Defina apenas o que o Payment Orchestrator in Clojure precisa.

Primeira versão:

```text
create payment
fetch payment
cancel payment
refund payment
capabilities
```

Capture/authorize separado somente se o produto realmente usar.

---

# 3. Interface conceitual

```clojure
(defprotocol PaymentGateway
  (capabilities [gateway])

  (create-payment!
    [gateway command])

  (fetch-payment
    [gateway command])

  (cancel-payment!
    [gateway command])

  (refund-payment!
    [gateway command]))
```

O protocol pode evoluir. O importante é a direção da dependência.

---

# 4. Commands canônicos

Exemplo:

```clojure
{:operation/id #uuid "..."
 :payment/id #uuid "..."
 :amount 12990
 :currency :BRL
 :method :card
 :customer {:reference "cust-123"}
 :idempotency-key "..."}
```

Não inclua:

```text
stripe_payment_method_types
asaas_billingType
```

no command comum.

---

# 5. Results canônicos

```clojure
{:provider :stripe
 :reference "..."
 :status :provider.status/processing
 :action nil
 :provider-request-id "..."
 :raw-status "requires_confirmation"}
```

`raw-status` pode existir para diagnóstico interno, mas não deve dirigir regra de negócio fora do mapper/adapter.

---

# 6. Status provider vs payment status

Provider status é uma camada de tradução.

```text
provider.status/processing
provider.status/requires-action
provider.status/succeeded
provider.status/failed
provider.status/cancelled
```

Payment domain então decide a transição.

Isso evita:

```text
Stripe mapper alterando diretamente Payment
```

---

# 7. Payment Action

Alguns pagamentos exigem ação do cliente.

Modelo canônico:

```clojure
{:action/type :redirect
 :action/url "..."}

{:action/type :qr-code
 :action/payload "..."
 :action/expires-at ...}

{:action/type :client-secret
 :action/value "..."}
```

Cuidado: alguns valores são sensíveis e não devem ser persistidos/logados indiscriminadamente.

---

# 8. Error taxonomy

Não deixe cada provider lançar qualquer exception até o handler.

Normalize:

```text
:provider.error/invalid-request
:provider.error/authentication
:provider.error/declined
:provider.error/rate-limited
:provider.error/unavailable
:provider.error/timeout
:provider.error/outcome-unknown
:provider.error/unexpected-response
```

Uma exception pode carregar:

```clojure
{:category ...
 :provider ...
 :provider-code ...
 :retryable? ...
 :outcome-known? ...}
```

---

# 9. Retryability NÃO é suficiente

Um erro pode ser tecnicamente retryable, mas financeiramente perigoso.

Exemplo:

```text
socket timeout
```

não informa se cobrança foi criada.

Por isso registre também:

```text
outcome-known?
```

---

# 10. Capabilities

Interface:

```clojure
(capabilities gateway)
```

Retorna apenas recursos implementados:

```clojure
#{:payment/create
  :payment/fetch
  :payment/refund
  :payment/cancel
  :method/card
  :method/pix}
```

Depois:

```text
:method/boleto
:payment/partial-refund
```

---

# 11. Feature negotiation

Antes da operação:

```clojure
(require-capability! gateway :method/pix)
```

Erro canônico:

```text
payment_method_not_supported
```

Não deixe isso virar exception obscura do provider.

---

# 12. Stripe Adapter

## Entrada

Canonical command.

## Mapper outbound

Traduz para parâmetros Stripe.

## Client

Executa request.

## Mapper inbound

Traduz resposta.

## Idempotency

Utilizar chave estável por operação.

## Webhook

Traduz event type + payload para evento canônico.

---

# 13. Asaas Adapter

Mesma estrutura.

As diferenças ficam confinadas.

```text
auth
request shape
response shape
event names
webhook validation
capabilities
```

---

# 14. Contract tests

Crie uma suite compartilhada.

Pseudo estrutura:

```clojure
(defn run-payment-gateway-contract [fixture]
  (testing "create returns canonical reference" ...)
  (testing "fetch returns canonical status" ...)
  (testing "errors use canonical taxonomy" ...)
  (testing "unsupported capability fails canonically" ...))
```

Execute para:

```text
FakeGateway
StripeGateway
AsaasGateway
```

---

# 15. Mapper tests

Fixtures devem ficar versionadas.

```text
test/fixtures/stripe/
test/fixtures/asaas/
```

Casos:

```text
success
decline
processing
unknown field
new field
malformed response
```

Parser deve preferencialmente tolerar campos novos que não utiliza.

---

# 16. Webhook mapping

Stripe:

```text
external event
 -> stripe webhook mapper
 -> canonical provider event
```

Asaas:

```text
external event
 -> asaas webhook mapper
 -> canonical provider event
```

Canonical:

```clojure
{:event/provider :stripe
 :event/external-id "evt..."
 :event/type :provider.payment/succeeded
 :event/provider-reference "..."
 :event/occurred-at ...}
```

Depois application layer decide qual Payment sofre transição.

---

# 17. Provider replacement test

Crie um projeto/teste consumidor pequeno.

Ele conhece somente:

```http
POST /v1/payments
GET /v1/payments/:id
```

Rode contra Stripe.

Depois contra Asaas.

O código consumidor deve ser exatamente o mesmo.

Salve essa demo no repositório.

---

# 18. Provider-specific functionality

Quando uma capability não possui equivalente:

Opção A:

```text
canonical capability específica
```

se for conceito de pagamento real.

Opção B:

```text
provider extension
```

se for genuinamente particular.

Evite adicionar campos genéricos como:

```json
"providerOptions": {}
```

na API pública cedo demais, porque isso recria acoplamento pelo backdoor.

---

# 19. Configuração

Exemplo:

```clojure
{:payments
 {:default-provider :stripe
  :providers
  {:stripe {:enabled true
            :secret #env "STRIPE_SECRET"}
   :asaas {:enabled true
           :secret #env "ASAAS_SECRET"}}}}
```

Secrets reais nunca entram no Git.

---

# 20. Critério para considerar provider implementado

Um provider só está "pronto" quando possui:

- adapter;
- mapper;
- error mapping;
- timeout;
- idempotency quando suportada/aplicável;
- webhook;
- auth verification;
- contract tests;
- fixtures;
- sandbox integration test;
- observabilidade;
- documentação de failure modes.
