# Migrações de banco em produção

## Estado protegido

O marco local anterior ao endurecimento é:

- backend: commit `b33d1bf`, tag `baseline-pre-hardening-20260728`;
- frontend: commit `cf871db`, tag `baseline-pre-hardening-20260728`.

Essas referências são locais e não representam implantação em produção.

O Hibernate ainda usa `update` por padrão para preservar o funcionamento atual.
A configuração de produção aceita a variável:

```text
HIBERNATE_SCHEMA_GENERATION=update
```

Somente depois da reconciliação e homologação descritas abaixo ela poderá ser
alterada para `validate`. Não usar `drop-and-create` em nenhum ambiente que
contenha dados reais.

## Bloqueios atuais

O catálogo em `src/main/resources/db/migration` ainda não pode ser entregue a
um executor automático:

- há versões duplicadas (`V25` e `V47`);
- há scripts SQL sem versão;
- parte do esquema também é alterada pelo Hibernate e por inicializadores Java.

O inventário pode ser repetido sem alterar o banco:

```powershell
.\deploy\verificar-migracoes.ps1
.\deploy\verificar-migracoes.ps1 -Strict
```

O modo `-Strict` deve falhar enquanto existir qualquer conflito.

Para identificar quais mudanças já existem, executar somente em uma cópia
restaurada do banco:

```text
psql <conexao_da_copia> -v ON_ERROR_STOP=1 \
  -f deploy/auditar-esquema-migracoes.sql
```

O relatório é somente leitura e cobre os objetos das versões duplicadas e dos
scripts sem versão. Guardar o resultado junto ao registro da implantação.

## Caminho de transição

1. Gerar um inventário do esquema real de produção e comparar com as entidades,
   inicializadores Java e scripts SQL.
2. Resolver as versões duplicadas sem renomear cegamente scripts que já possam
   ter sido aplicados. Registrar, por unidade, quais alterações existem.
3. Converter scripts sem versão em migrações idempotentes ou incorporá-los a
   uma linha de base formal.
4. Escolher um único mecanismo de migração e uma tabela de histórico.
5. Restaurar uma cópia recente do banco em homologação.
6. Executar a linha de base e as migrações sobre essa cópia.
7. Iniciar a aplicação com `HIBERNATE_SCHEMA_GENERATION=validate` e executar os
   testes clínicos e administrativos críticos.
8. Desativar o inicializador legado com
   `GHBAM_CLINICAL_SCHEMA_INITIALIZER_ENABLED=false`.
9. Somente após aprovação, programar backup, migração e implantação em uma
   janela controlada.

## Linha Flyway V2

A linha nova usa:

- localização `db/migration-v2`;
- tabela `ghbam_schema_history_v2`;
- baseline `68`;
- execução no startup desativada por padrão.

Para homologação controlada, usar simultaneamente:

```text
GHBAM_FLYWAY_MIGRATE_AT_START=true
HIBERNATE_SCHEMA_GENERATION=validate
GHBAM_CLINICAL_SCHEMA_INITIALIZER_ENABLED=false
```

Não habilitar essas opções em produção antes de repetir a auditoria em uma
cópia recente do banco original e aprovar formalmente o resultado.

## Backup e validação

Antes de qualquer alteração estrutural, produzir um backup PostgreSQL no
formato custom e validar sua restauração em uma base isolada:

```text
pg_dump -Fc --dbname=<conexao> --file=<backup.dump>
pg_restore --list <backup.dump>
pg_restore --clean --if-exists --dbname=<base_isolada> <backup.dump>
```

As credenciais não devem aparecer em scripts, histórico do shell ou
repositório. O backup só é considerado válido depois de uma restauração
completa e de verificações de contagem e integridade.

## Estratégia de mudança e retorno

Preferir migrações expansivas e compatíveis:

1. adicionar novas estruturas sem remover as antigas;
2. implantar código compatível com ambas;
3. migrar e conferir dados;
4. remover estruturas antigas em uma implantação posterior.

O retorno da aplicação usa o artefato e a tag anteriores. Mudanças de banco
devem, preferencialmente, ser corrigidas por uma nova migração para frente.
Restauração integral do backup é último recurso e exige janela, validação e
aceitação explícita da possível perda de dados posteriores ao backup.
