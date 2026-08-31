# Multi-tenancy (M17)

Each payment is owned by `merchant-id`. API clients select the merchant boundary using `X-Merchant-Id`; absent header means the backwards-compatible `default` merchant. Reads, history, and ledger routes return `404` outside that boundary. The ownership attribute is persisted in Datomic.

This milestone establishes isolation and the Merchant/ProviderAccount vocabulary. M18 adds an application-level routing policy: a merchant can select a configured provider without changing the public payment request. See [Provider routing](#provider-routing-m18).

## Provider routing (M18)

Routing is evaluated before the payment is persisted or sent to a provider. The policy is pure and receives the merchant, canonical currency, canonical payment method, routing configuration, and a catalog of available provider descriptors. Its precedence is merchant, currency, payment method, then default. A `:routing.strategy/lowest-cost` policy chooses the lowest numeric `:cost` among compatible, available providers.

An unavailable or incompatible configured provider returns a canonical provider-unavailable error and increments `provider_routing_errors_total`. The orchestrator does **not** retry the same operation through another provider: especially after an unknown outcome, that would risk a duplicate charge. M18 supports the Fake and Stripe adapters currently present in this repository; a merchant route to a provider that is not configured fails safely.

Local configuration is under `:payments` in `resources/config/base.edn`. Keep Stripe disabled unless its environment credentials are configured. The `PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER` environment variable remains an explicit local override of the default route.
