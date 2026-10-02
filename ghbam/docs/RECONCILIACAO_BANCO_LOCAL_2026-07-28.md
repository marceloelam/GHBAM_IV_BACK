# Reconciliação da base local de homologação

Data: 28/07/2026

Escopo: `localhost:5432/hospitalit`, informado como ambiente local de
homologação. Nenhuma alteração foi feita na base original.

## Backup e restauração

- backup custom criado em
  `C:\tmp\hospitalit-homolog-20260728-2256.dump`;
- tamanho final do arquivo: 837.151.269 bytes;
- catálogo validado pelo `pg_restore`: 1.053 entradas;
- cópia restaurada em `hospitalit_restore_test_20260728`;
- nenhum índice inválido ou incompleto foi encontrado.

Contagens comparadas:

| Objeto | Original | Restaurada |
|---|---:|---:|
| BAM | 492.726 | 492.726 |
| Pacientes | 145.722 | 145.722 |
| Registros | 494.601 | 494.601 |
| Avaliações | 494.284 | 494.284 |
| Novas internações | 27.692 | 27.692 |
| Documentos assistenciais | 0 | 0 |
| Histórico Flyway | 5 | 5 |

A diferença de tamanho físico entre original (7.313 MB) e restaurada
(7.330 MB) é compatível com reconstrução física de tabelas e índices; as
contagens lógicas verificadas são iguais.

## Histórico Flyway encontrado

A base contém uma linha de base em `V64` e quatro migrações bem-sucedidas:

| Versão | Script | Checksum |
|---|---|---:|
| 65 | `V65__widen_servidor_password_hash.sql` | 118976978 |
| 66 | `V66__move_runtime_ddl_to_flyway.sql` | 986403392 |
| 67 | `V67__complete_legacy_clinical_schema.sql` | -1699652056 |
| 68 | `V68__add_keypad_password_verifier.sql` | 1334918981 |

Esses quatro arquivos não existem no código local nem no JAR atualmente
armazenado no servidor. O JAR contém outros arquivos chamados `V65` e `V66`.
Logo, o catálogo atual não deve ser ligado ao histórico existente e não se
deve executar `flyway repair` para mascarar a divergência.

## Estrutura verificada

- todas as colunas previstas pelos scripts duplicados `V25` e `V47` existem;
- todos os objetos dos cinco scripts sem versão também existem;
- `documento_assistencial` existe, mas os índices
  `idx_documento_assistencial_paciente` e
  `idx_documento_assistencial_bam` não existem;
- `servidor2` contém tanto `correcaobam` quanto a coluna corrompida
  `correcãobam`;
- o código Java utiliza somente `correcaobam`.
- na cópia restaurada, `correcaobam` possui 9 permissões ativas e a coluna
  corrompida possui 0; existem 9 divergências. Portanto, não há valor verdadeiro
  a migrar da coluna corrompida.

## Decisão segura proposta

1. Tratar todo o catálogo até `V68` como legado já absorvido pela linha de base.
2. Preservar `flyway_schema_history` como evidência histórica, sem repará-lo.
3. Iniciar uma linha controlada em uma tabela de histórico separada, depois de
   aprovação explícita e teste completo na cópia restaurada.
4. Criar novas migrações a partir de `V69` para os índices ausentes e, em etapa
   separada, consolidar a coluna de permissão corrompida.
5. Só depois ativar `validate` e desativar o inicializador Java legado.

## Resultado da linha V2 na cópia restaurada

A linha isolada foi testada exclusivamente em
`hospitalit_restore_test_20260728`:

- baseline `68` criado em `ghbam_schema_history_v2`;
- `V69` criou os dois índices de `documento_assistencial`;
- `V70` removeu a coluna corrompida sem alterar `correcaobam`;
- as três entradas do histórico V2 estão com `success=true`;
- não existem índices inválidos;
- uma segunda inicialização informou que o esquema já estava na versão `70`;
- Hibernate iniciou com `validate`;
- `ClinicalSchemaInitializer` permaneceu desativado;
- backend iniciou em porta local alternativa;
- `/q/health` respondeu HTTP `200`, estado `UP`, banco `UP`;
- os 47 testes Maven passaram e o pacote foi construído com sucesso.

O teste também corrigiu a dependência de health: a biblioteca SmallRye direta
foi substituída pela extensão `quarkus-smallrye-health`.

O catálogo V2 pode ser validado isoladamente com:

```powershell
.\deploy\verificar-migracoes.ps1 `
  -MigrationDir src\main\resources\db\migration-v2 `
  -Strict
```

Esses resultados não descrevem nem autorizam alteração no banco original de
produção.
