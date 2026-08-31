# ADR-0009: Double-entry ledger imutável

## Contexto

O estado retornado por um provider descreve a operação externa, mas não é a fonte de verdade dos lançamentos financeiros internos.

## Decisão

Cada pagamento que alcança `paid` gera uma única journal transaction `payment-settled`, identificada por `payment-settled:<payment-id>`. Ela contém postings em unidades monetárias menores:

- débito em `processor-receivable`;
- crédito em `merchant-payable`.

O domínio puro recusa journals não balanceados antes da persistência. Contas, journal e postings são entidades Datomic imutáveis; não há atributo de saldo mutável. A chave econômica única evita duplicação por replays e webhooks duplicados.

## Consequências

Taxas, estornos e repasses serão journals adicionais, cada qual balanceado. Saldo será derivado de postings, nunca atualizado diretamente.
