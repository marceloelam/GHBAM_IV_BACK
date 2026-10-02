-- Auditoria somente leitura para reconciliar o catálogo de migrações.
-- Execute primeiro em uma cópia restaurada do banco de produção.
-- Este arquivo contém apenas SELECT e não altera objetos nem dados.

SELECT
    current_database() AS banco,
    current_user AS usuario,
    current_setting('server_version') AS postgres_versao,
    now() AS auditado_em;

SELECT
    to_regclass('public.flyway_schema_history') AS flyway_schema_history,
    to_regclass('public.databasechangelog') AS liquibase_databasechangelog;

WITH objetos(tipo, tabela, coluna) AS (
    VALUES
        ('V25', 'chat_mensagens', 'remetente_foto'),
        ('V25', 'chat_mensagens', 'destinatario_foto'),
        ('V25', 'avaliacao', 'hpp'),
        ('V47', 'prescricao', 'quem'),
        ('SEM_VERSAO', 'servidor2', 'auditoriaclinica'),
        ('SEM_VERSAO', 'servidor2', 'especialidade'),
        ('SEM_VERSAO', 'registro', 'temlabo'),
        ('SEM_VERSAO', 'registro', 'temrx'),
        ('SEM_VERSAO', 'registro', 'temtomo'),
        ('SEM_VERSAO', 'registro', 'temmed'),
        ('SEM_VERSAO', 'registro', 'temenfe'),
        ('SEM_VERSAO', 'bam', 'prontuario_idprontuario')
)
SELECT
    objetos.tipo,
    objetos.tabela,
    objetos.coluna,
    columns.column_name IS NOT NULL AS existe,
    columns.data_type,
    columns.is_nullable,
    columns.column_default
FROM objetos
LEFT JOIN information_schema.columns AS columns
    ON columns.table_schema = 'public'
   AND columns.table_name = objetos.tabela
   AND columns.column_name = objetos.coluna
ORDER BY objetos.tipo, objetos.tabela, objetos.coluna;

SELECT
    'V47' AS tipo,
    'documento_assistencial' AS objeto,
    to_regclass('public.documento_assistencial') IS NOT NULL AS existe
UNION ALL
SELECT
    'V47',
    'idx_documento_assistencial_paciente',
    to_regclass('public.idx_documento_assistencial_paciente') IS NOT NULL
UNION ALL
SELECT
    'V47',
    'idx_documento_assistencial_bam',
    to_regclass('public.idx_documento_assistencial_bam') IS NOT NULL;

SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'servidor2'
  AND (
      column_name IN (
          'aguardandoatendimento', 'atendimentoperiodo', 'biometria',
          'comunicacaoespecialistas', 'correcaobam', 'enfermariaseleitos',
          'imprimirbam', 'limparpainel', 'pacientesematendimento',
          'agendamentomedico', 'cadastrarsalas', 'cadastrarservidores',
          'cadastrarsetores', 'cadastrarunidades', 'auditoriaclinica',
          'emapropriatendimento', 'listaservidores', 'mapapacientes',
          'permissoes', 'resumopacientes', 'usuariosonline',
          'chatprofissionais', 'pedidosexame', 'balancohidrico', 'ccihresumo',
          'driveenfermagem', 'escalaenfermagem',
          'evolucaomultidisciplinar', 'minhasassinaturas',
          'avaliacaopacienteia', 'bulariomedico', 'ciclomedicacao',
          'historicocompleto', 'painelenfermagem', 'biexecutivo',
          'nirrestaurarinternacao', 'nir', 'painelnir', 'localcenso',
          'pesquisanir', 'nirrelatorio', 'setorcenso',
          'internadosvisitaimp'
      )
      OR column_name LIKE 'corre%bam'
  )
ORDER BY column_name;

-- Detecta se a coluna criada com nome corrompido contém algum valor que ainda
-- precise ser preservado. Não retorna usuários nem outros dados pessoais.
SELECT
    count(*) FILTER (WHERE correcaobam) AS permissao_correta_true,
    count(*) FILTER (WHERE "correcãobam") AS permissao_corrompida_true,
    count(*) FILTER (
        WHERE correcaobam IS DISTINCT FROM "correcãobam"
    ) AS valores_divergentes
FROM servidor2;
