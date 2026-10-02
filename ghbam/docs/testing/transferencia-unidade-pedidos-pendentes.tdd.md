# Transferência de unidade com pedidos pendentes

## Origem

Jornadas derivadas da solicitação: ao transferir um paciente entre unidades, os pedidos operacionais pendentes do BAM precisam aparecer na fila da unidade de destino, enquanto os itens já realizados mantêm a unidade em que foram executados.

## Evidências

| Garantia | Teste | Resultado |
|---|---|---|
| A transferência atualiza a unidade de `pedidomedico2` apenas quando `efetuado = false`. | `BamRepositoryTransferenciaTest` | PASS |
| O BAM, a avaliação e os pedidos pendentes são processados pelo mesmo fluxo de transferência. | `BamRepositoryTransferenciaTest` | PASS |
| A transferência marca o BAM como transferido. | `BamRepositoryTransferenciaTest` | PASS |

RED: `mvn -Dtest=BamRepositoryTransferenciaTest test` falhou inicialmente com `NoSuchMethodException` para o comportamento ausente.

GREEN: `mvn -Dtest=BamRepositoryTransferenciaTest test` passou com 1 teste, 0 falhas e 0 erros.

O marcador é exposto nas filas de atendimento médico e agendado. A criação de uma alocação em internação/observação o remove do BAM.

Cobertura: `mvn -Dtest=BamRepositoryTransferenciaTest test jacoco:report` gerou o relatório. A classe legada `BamRepository` está em 65 instruções cobertas e 188 não cobertas, portanto a cobertura global da classe não alcança 80%; o teste cobre especificamente a regra nova.

## Limites deliberados

O fluxo não altera pedidos já executados nem resultados laboratoriais, para preservar a rastreabilidade da unidade de origem.
