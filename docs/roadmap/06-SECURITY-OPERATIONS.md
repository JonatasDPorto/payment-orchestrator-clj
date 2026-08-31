# Payment Orchestrator in Clojure — Segurança, Resiliência e Operação

> Este documento não substitui revisão de segurança profissional, requisitos legais ou compliance aplicável a uma operação financeira real.

---

# 1. Security boundary

O Payment Orchestrator in Clojure deve tentar reduzir ao máximo dados de cartão sob sua responsabilidade.

Prefira provider-hosted/tokenized flows.

Não desenhe uma API que peça:

```json
{
  "cardNumber": "...",
  "cvv": "..."
}
```

sem uma necessidade e arquitetura de compliance muito bem estabelecidas.

---

# 2. Secrets

Secrets:

```text
STRIPE_SECRET
STRIPE_WEBHOOK_SECRET
ASAAS_SECRET
ASAAS_WEBHOOK_TOKEN
API_AUTH_SECRET
```

Nunca:

- Git;
- README;
- fixture;
- log;
- exception retornada.

Use ambiente local seguro e secret manager em produção.

---

# 3. Authentication

Primeiro consumidor pode usar API key de serviço.

Header exemplo:

```text
Authorization: Bearer <token>
```

Produção maior pode justificar:

```text
mTLS
OAuth2 client credentials
signed requests
```

Não implemente tudo por portfólio.

---

# 4. Authorization

Quando multi-tenant:

```text
merchant A cannot read merchant B
```

Isso precisa estar no repository/query boundary, não apenas na UI.

---

# 5. Webhook authenticity

## Stripe

- validar assinatura;
- usar raw request body conforme exigência do mecanismo de assinatura;
- configurar secret por endpoint;
- considerar tolerância temporal/replay conforme documentação atual.

## Asaas

- configurar token próprio de webhook;
- validar `asaas-access-token`;
- nunca usar API key principal como webhook auth token.

---

# 6. Webhook acknowledgement

Estratégia:

```text
verify
persist unique event
ack
process async
```

No Asaas, siga exatamente o comportamento atual esperado pelo provider para status de sucesso.

---

# 7. Idempotency

Existem pelo menos três níveis:

## Consumer -> Payment Orchestrator in Clojure

`Idempotency-Key`

## Payment Orchestrator in Clojure -> Provider

provider-specific idempotency mechanism quando disponível.

## Provider -> Payment Orchestrator in Clojure

unique provider event ID.

São problemas relacionados, mas diferentes.

---

# 8. Timeouts

Defina explicitamente:

```text
connect timeout
request timeout
pool timeout
```

Nunca dependa de defaults infinitos/desconhecidos.

---

# 9. Retry

Retry com:

```text
bounded attempts
exponential backoff
jitter
classification
```

Não retry automaticamente operação de outcome desconhecido sem garantia idempotente apropriada.

---

# 10. Circuit breaker

Pode ser adicionado posteriormente se houver valor operacional.

Não é obrigatório para v1.

Primeiro tenha:

- timeout;
- error classification;
- metrics;
- retry policy.

---

# 11. Rate limiting

Proteja:

```text
payment creation
webhook endpoints contra abuso volumétrico
audit endpoints caros
```

Mas webhook allowlisting por IP não deve ser presumida como mecanismo suficiente se provider oferece assinatura/token.

---

# 12. PII

Classifique campos.

Exemplos:

```text
customer id
email
name
tax id
billing address
provider payload
```

Defina:

```text
needed?
retention?
encrypted?
logged?
```

---

# 13. Log redaction

Crie função central.

Redigir:

```text
Authorization
API keys
client secrets
webhook tokens
payment method secret values
```

---

# 14. Error response

Usuário recebe:

```json
{
  "error": {
    "code": "provider_unavailable",
    "message": "Payment provider is temporarily unavailable"
  }
}
```

Não:

```text
java stacktrace
provider secret
full raw response
```

Detalhe técnico vai para observabilidade interna sanitizada.

---

# 15. Operational states

Separe:

```text
business state
provider state
operation state
processing state
```

Exemplo:

Payment pode continuar:

```text
processing
```

enquanto ProviderOperation está:

```text
outcome-unknown
```

Isso evita inventar estados financeiros incorretos.

---

# 16. Alerting

Alertas úteis:

```text
provider error rate > threshold
webhook lag
provider event failed
reconciliation mismatch
relay lag
ledger invariant violation
API p99
```

---

# 17. Runbooks

Crie `docs/runbooks/`.

Mínimo:

```text
provider-outage.md
webhook-queue-stuck.md
reconciliation-mismatch.md
kafka-down.md
datomic-errors.md
```

Runbook deve responder:

```text
how to detect
impact
safe actions
unsafe actions
recovery
verification
```

---

# 18. Disaster thinking

Perguntas:

- se worker morrer, eventos permanecem?
- se API reiniciar, operações são recuperáveis?
- se webhook parar por horas, reconciliation ajuda?
- se Kafka cair, pagamento continua funcionando?
- se provider cair, que endpoints degradam?
- existe algo que depende de memória local?

---

# 19. Dependency failure isolation

Kafka não deve impedir persistência do pagamento se ele é apenas downstream event delivery.

Dashboard não deve impedir webhook.

Metrics backend não deve impedir transação financeira.

---

# 20. Audit trail

Toda ação administrativa deve registrar:

```text
actor
reason
request
time
affected aggregate
```

---

# 21. Manual correction

Em sistemas reais algumas divergências exigem intervenção.

Não crie endpoint genérico:

```http
POST /force-status
```

sem controle.

Uma eventual operação administrativa precisa:

- regra explícita;
- auth forte;
- reason obrigatório;
- audit;
- invariantes;
- teste.

---

# 22. Backup/retention

Defina política de retenção para:

```text
provider payload
logs
metrics
audit
ledger
```

Não confunda imutabilidade Datomic com obrigação de guardar qualquer dado para sempre.

Requisitos legais e de privacidade podem exigir desenho específico.

---

# 23. Threat model inicial

## Spoofing

Webhook falso.

Mitigação: assinatura/token.

## Tampering

Alteração de request.

Mitigação: TLS, auth, signature onde aplicável.

## Repudiation

"não fiz essa operação".

Mitigação: tx metadata/audit.

## Information disclosure

Secrets/PII em logs.

Mitigação: redaction/minimização.

## Denial of service

Webhook/API flood.

Mitigação: limits, queue, capacity controls.

## Elevation of privilege

Merchant lê outro merchant.

Mitigação: authorization boundaries.

---

# 24. Security Definition of Done

Antes de produção real:

- threat model revisado;
- secrets externos;
- auth;
- webhook verification;
- rate limits apropriados;
- logs sanitizados;
- PII inventory;
- dependency scans;
- backups/recovery;
- runbooks;
- alerts;
- provider sandbox tests;
- ambiente real revisado.

Para operação financeira de verdade, faça revisão adicional adequada ao seu contexto.
