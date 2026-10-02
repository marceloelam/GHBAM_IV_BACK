# Evidências TDD — modo Ultrassonografia em procedimentos RX

## Jornada

Como responsável pelo faturamento, preciso cadastrar um procedimento com modo `ULTRASSONOGRAFIA`, sem erro 400 de validação nem erro 500 por limite da coluna.

## Garantias

| Garantia | Teste | Tipo | Resultado |
|---|---|---|---|
| `ULTRASSONOGRAFIA` é aceito como modo | `ProcedimentosRXServiceTest.deveAceitarUltrassonografiaComoModo` | Unitário | PASS |
| A entidade comporta o valor completo | `ProcedimentosRXServiceTest.colunaModoDeveComportarUltrassonografia` | Unitário | PASS |
| O DTO comporta o valor completo | `ProcedimentosRXServiceTest.dtoModoDeveComportarUltrassonografia` | Unitário | PASS |
| Modos desconhecidos continuam rejeitados | `ProcedimentosRXServiceTest.deveRejeitarModoDesconhecido` | Unitário | PASS |

## Evidências

- RED: o teste não compilou porque a validação ainda era privada; a falha preservou o comportamento não testável e ainda rejeitado.
- GREEN: `.\\mvnw.cmd -Dtest=ProcedimentosRXServiceTest test` passou após incluir o modo e ampliar a coluna.
- Banco: `V85__expand_rxprocedimentos_modo.sql` altera `rxprocedimentos.modo` para `VARCHAR(30)`.

## Publicação

A migração exige Flyway habilitado no deploy (`GHBAM_FLYWAY_MIGRATE_AT_START=true`) ou execução controlada equivalente antes da validação de esquema em produção.
