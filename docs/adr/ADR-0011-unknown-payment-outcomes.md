# ADR-0011: Resultados ambíguos exigem reconciliação

## Status

Accepted.

## Decisão

Um timeout sem confirmação do provider não é falha financeira. A operação externa é persistida com status `outcome-unknown`, o pagamento permanece `processing` e uma reconciliação consulta o provider usando a referência conhecida. Não há retry automático de criação nem fallback para outro provider.

O worker registra cada decisão de reconciliação com metadata temporal. Quando o remoto confirma sucesso, a transição canônica para `paid` ocorre uma vez; quando a consulta não permite conclusão, o caso fica pendente para revisão/retry operacional.
