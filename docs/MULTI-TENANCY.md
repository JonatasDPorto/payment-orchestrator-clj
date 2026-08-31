# Multi-tenancy (M17)

Each payment is owned by `merchant-id`. API clients select the merchant boundary using `X-Merchant-Id`; absent header means the backwards-compatible `default` merchant. Reads, history, and ledger routes return `404` outside that boundary. The ownership attribute is persisted in Datomic.

This milestone establishes isolation and the Merchant/ProviderAccount vocabulary. Provider selection by merchant is deliberately deferred to M18; the configured gateway remains process-wide until routing policy is introduced.
