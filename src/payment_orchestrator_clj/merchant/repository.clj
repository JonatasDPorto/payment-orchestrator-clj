(ns payment-orchestrator-clj.merchant.repository)

(defprotocol MerchantRepository
  (save-merchant! [repository merchant context])
  (find-merchant [repository merchant-id]))

(defprotocol ProviderAccountRepository
  (save-provider-account! [repository account context])
  (find-provider-account [repository merchant-id account-id])
  (find-provider-account-by-webhook-identity [repository provider webhook-identity]))

(defprotocol MerchantProviderConfigurationRepository
  (save-provider-configuration! [repository configuration context])
  (provider-configurations [repository merchant-id]))
