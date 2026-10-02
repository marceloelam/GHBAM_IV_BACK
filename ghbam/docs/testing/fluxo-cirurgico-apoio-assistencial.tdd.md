# Evidência TDD — fluxo cirúrgico e apoio assistencial

Origem: jornadas derivadas da solicitação para integrar Centro Cirúrgico, CME, Hemoterapia, histórico, Jornada PA e relatórios.

| Garantia | Teste | Resultado |
|---|---|---|
| Toda cirurgia gera demanda de CME; hemoterapia só quando indicada | `FluxoCirurgicoAssistencialTest` | PASS |
| Cirurgia sem hemocomponente não gera demanda de hemoterapia | `FluxoCirurgicoAssistencialTest` | PASS |
| Início do ato exige as liberações aplicáveis | `FluxoCirurgicoAssistencialTest` | PASS |

RED: `mvn -Dtest=FluxoCirurgicoAssistencialTest test` falhou porque `FluxoCirurgicoAssistencial` ainda não existia.

GREEN: `mvn -Dtest=FluxoCirurgicoAssistencialTest test` — 3 testes, 0 falhas, 0 erros.

Validação de frontend: `npx.cmd tsc -b` — concluído sem erros. O build Vite completo excedeu o limite de execução disponível nesta sessão após iniciar a transformação; não há resultado final registrado para ele.
