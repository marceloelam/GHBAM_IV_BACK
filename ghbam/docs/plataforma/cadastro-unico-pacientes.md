# Cadastro único de pacientes (todos os municípios)

Decisão de 17/11/2026: um cadastro de pacientes para todos os municípios. Não se copia paciente de um banco para outro.

## Como funciona

- A tabela `pacientes` existe só no banco principal (`hospitalit`).
- No banco de cada município, `pacientes` é uma tabela estrangeira (`postgres_fdw`) apontando para `hospitalit.pacientes`.
  O código não muda: busca, cadastro, BAM e histórico leem e gravam direto no cadastro central.
- O clínico (BAM, atendimento, exames, prescrição) continua no banco de cada município.
- Paciente novo cadastrado num município recebe número da faixa daquele município (`ghbam.municipios.faixas`, ex.: Nova Iguaçu a partir de 10.000.001). Número e prontuário ficam únicos na plataforma.
- Histórico completo (todas as unidades) junta os BAMs do mesmo paciente nos outros municípios (somente leitura).

## Aplicar num município novo (banco recém-criado, sem pacientes)

Script de referência usado em `municipio_330350` (homologação, 17/11/2026):

1. `CREATE EXTENSION postgres_fdw;`
2. `CREATE SERVER ghbam_plataforma FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host '<host do principal>', port '5432', dbname 'hospitalit', fetch_size '1000', use_remote_estimate 'true');`
3. `CREATE USER MAPPING FOR <usuario da aplicacao> SERVER ghbam_plataforma OPTIONS (user '...', password '...');`
4. Remover as visões `worklist_orthanc_v` e `worklist_v` e as chaves estrangeiras que apontam para `pacientes` (`bam`, `novointernado`, `novointernadoadm`, `escalapaciente`, `farmaciaentrega`). Chave estrangeira para tabela remota não existe no PostgreSQL.
5. `ALTER SEQUENCE pacientes_idpaciente_seq OWNED BY NONE;` e `ALTER TABLE pacientes RENAME TO pacientes_local_desativada;` (vazia, guardada para reversão).
6. `IMPORT FOREIGN SCHEMA public LIMIT TO (pacientes) FROM SERVER ghbam_plataforma INTO public OPTIONS (import_default 'true', import_not_null 'true');`
7. `ALTER FOREIGN TABLE pacientes ALTER COLUMN idpaciente SET DEFAULT nextval('public.pacientes_idpaciente_seq');` (faixa do município).
8. Recriar as duas visões com a definição original.

## Reverter (só com o banco do município ainda sem uso real)

1. `DROP VIEW worklist_orthanc_v, worklist_v;` e `DROP FOREIGN TABLE pacientes;`
2. `ALTER TABLE pacientes_local_desativada RENAME TO pacientes;` e devolver o default `nextval('pacientes_idpaciente_seq')`.
3. Recriar as 5 chaves estrangeiras e as 2 visões.
4. `DROP USER MAPPING FOR <usuario> SERVER ghbam_plataforma; DROP SERVER ghbam_plataforma;`

## Cuidados

- Migration que altera `pacientes` vale só no banco principal; nos municípios a tabela é remota.
- Itaguaí numera abaixo de 10.000.000; cada município novo recebe a faixa seguinte (20.000.000, 30.000.000...).
- Falta automatizar estes passos no "Ativar município".
