# Evidências TDD — sequência de rxprocedimentos

## Jornada

Como responsável pelo faturamento, preciso cadastrar um procedimento sem colisão de chave primária após cargas ou inserções legadas.

## Garantias

| Garantia | Teste | Tipo | Resultado |
|---|---|---|---|
| Existe migração versionada para a sequência de `rxprocedimentos.idrx` | `FlywayV2ConfigurationContractTest.corrigeSequenciaDeRxProcedimentosAposCargaLegada` | Contrato | PASS |
| A sequência é descoberta pelo PostgreSQL, sem nome fixo | Mesmo teste | Contrato | PASS |
| O próximo ID considera `MAX(idrx)` | Mesmo teste | Contrato | PASS |

## Evidências

- RED: `.\\mvnw.cmd -Dtest=FlywayV2ConfigurationContractTest test` falhou com `NoSuchFileException` para a V86.
- GREEN: o mesmo teste passou após a criação da migração.
- Causa observada: o PostgreSQL tentou inserir `idrx=11`, já existente, indicando sequência abaixo dos dados atuais.

## Publicação

A V86 precisa ser executada pelo Flyway no banco utilizado pelo backend antes de repetir o cadastro.
