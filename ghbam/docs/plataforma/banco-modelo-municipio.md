# Banco modelo dos municípios

O botão **Criar banco** da tela Municípios (`POST /plataforma/municipios/{ibge}/criar-banco`) executa
`CREATE DATABASE municipio_<ibge> TEMPLATE ghbam_modelo_municipio` e marca o banco com
`COMMENT ON DATABASE ... IS 'ghbam-municipio faixa=N'`. O backend passa a rotear esse banco sem mexer em
`ghbam.municipios.bancos`/`faixas` (a configuração manual continua valendo e vence a marca).
A faixa é sempre 10.000.000 acima da maior já usada.

O modelo é preparado **uma vez por servidor** e não pode ter conexão aberta na hora de criar
(por isso fica com `ALLOW_CONNECTIONS false`). Nome configurável em `ghbam.municipios.modelo`.

## Forma usada na homologação (17/11/2026, sem parar o backend)

1. `createdb ghbam_modelo_municipio`
2. `pg_dump -d hospitalit --schema-only --no-owner --no-acl | psql -d ghbam_modelo_municipio`
3. `pg_dump -d hospitalit --data-only` só de `ghbam_schema_history_v2 s_pacbo s_pacid s_proced s_pasrv s_cid bulario ccbo rxprocedimentos faturamento_sigtap_importacao` → `psql -d ghbam_modelo_municipio`
4. Passos 1–8 de `cadastro-unico-pacientes.md` no modelo (host do FDW = servidor do banco principal).
5. `ALTER DATABASE ghbam_modelo_municipio WITH ALLOW_CONNECTIONS false IS_TEMPLATE true;`

## Alternativa: clonar um banco de município já pronto

Precisa de um momento sem conexões no banco de origem, ou seja, backend parado (e DBeaver/pgAdmin desconectados dele).
A origem pode ser um banco de município já pronto ou, se não houver nenhum, o próprio `hospitalit`.

```sql
-- no banco postgres
CREATE DATABASE ghbam_modelo_municipio TEMPLATE municipio_330350;   -- ou TEMPLATE hospitalit
```

Se a origem foi o `hospitalit`, depois do TRUNCATE abaixo aplique no modelo os passos 1–8 de
`cadastro-unico-pacientes.md` (tabela `pacientes` vira estrangeira, apontando para o cadastro central).

```sql
-- no banco ghbam_modelo_municipio: apaga os dados, mantém estrutura, catálogos e histórico de migrations.
-- NUNCA incluir tabela estrangeira (relkind 'f'): "pacientes" é o cadastro central via postgres_fdw.
DO $$
DECLARE t record;
BEGIN
  IF current_database() <> 'ghbam_modelo_municipio' THEN
    RAISE EXCEPTION 'Banco errado: %. Este script só roda no ghbam_modelo_municipio.', current_database();
  END IF;
  FOR t IN
    SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
      AND c.relname NOT IN ('ghbam_schema_history_v2', 's_pacbo', 's_pacid', 's_proced', 's_pasrv', 's_cid',
                            'bulario', 'ccbo', 'rxprocedimentos', 'faturamento_sigtap_importacao')
  LOOP
    EXECUTE format('TRUNCATE TABLE public.%I RESTART IDENTITY CASCADE', t.relname);
  END LOOP;
END $$;
-- conferir que os catálogos continuam com linhas (CASCADE não pode tê-los esvaziado)
SELECT 's_proced', count(*) FROM s_proced UNION ALL SELECT 's_cid', count(*) FROM s_cid;
```

```sql
-- no banco postgres
ALTER DATABASE ghbam_modelo_municipio WITH ALLOW_CONNECTIONS false IS_TEMPLATE true;
```

## Depois de criar

O município aparece como **Pronto para ativar**. **Ativar** aplica a faixa nos contadores, copia as
unidades daquele IBGE e o superusuário, e avisa as migrations que faltam (o modelo para no tempo:
atualize-o de vez em quando refazendo os passos acima, ou aplique as pendentes no banco novo).

## Desfazer (banco recém-criado, sem uso)

`DROP DATABASE municipio_<ibge>;` e reiniciar o backend (o pool aberto segura conexões).
