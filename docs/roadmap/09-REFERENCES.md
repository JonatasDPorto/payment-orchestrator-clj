# Payment Orchestrator in Clojure — Referências oficiais úteis

Estas referências foram escolhidas para apoiar decisões do roadmap. APIs externas mudam; confira a documentação oficial durante a implementação de cada adapter.

---

## Datomic

### Introdução

https://docs.datomic.com/

Pontos importantes:

- datoms imutáveis;
- audit trail;
- transações;
- modelo temporal.

### Client API

https://docs.datomic.com/client-api/datomic.client.api.html

Funções especialmente relevantes:

```text
transact
as-of
history
with
tx-range
```

### History tutorial

https://docs.datomic.com/client-tutorial/history.html

### Database filters

https://docs.datomic.com/reference/filters.html

### Log API

https://docs.datomic.com/reference/log.html

### Client Tutorial / Datomic Local

https://docs.datomic.com/client-tutorial/client.html

---

## Stripe

### Idempotent Requests

https://docs.stripe.com/api/idempotent_requests

Use a documentação atual durante implementação, pois comportamento pode variar conforme namespace/API version.

### Webhooks

https://docs.stripe.com/webhooks

Ponto importante:

A validação de assinatura depende do corpo original da requisição; não deixe middleware modificar o body antes da verificação.

---

## Asaas

### Webhooks

https://docs.asaas.com/docs/sobre-os-webhooks

### Idempotência de Webhooks

https://docs.asaas.com/docs/como-implementar-idempotencia-em-webhooks

Pontos importantes documentados atualmente:

- entrega at-least-once;
- o mesmo evento pode ser reenviado;
- persistir o ID;
- responder depois da persistência;
- processar trabalho demorado de forma assíncrona.

### Eventos

https://docs.asaas.com/docs/eventos-de-webhooks

### Criar webhook via API

https://docs.asaas.com/reference/create-new-webhook

---

# Nota

Não copie cegamente objetos externos para o canonical model.

Use a documentação para entender a semântica e depois faça o mapper:

```text
Provider Semantics
      |
      v
Payment Orchestrator in Clojure Semantics
```

Sempre revise:

```text
API version
authentication
idempotency behavior
webhook retry behavior
webhook signature/token
timeouts
rate limits
sandbox behavior
```

antes de colocar um provider em produção.
