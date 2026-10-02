-- Remove as colunas camelCase que o Hibernate criou por engano nas tabelas do
-- centro cirurgico.
--
-- As entidades Cirurgia* declaravam campos camelCase sem @Column(name=...), e a
-- V132 criou as tabelas em snake_case. Em dev, com
-- quarkus.hibernate-orm.database.generation=update, o Hibernate resolveu a
-- divergencia criando uma segunda coluna para cada campo ("dataPrevista" ao lado
-- de data_prevista). O insert gravava na coluna nova e a original NOT NULL ficava
-- nula: agendar cirurgia caia em ConstraintViolationException.
--
-- As entidades agora apontam para as colunas da V132. As colunas duplicadas nunca
-- foram lidas pela aplicacao - so existem em bases de desenvolvimento que rodaram
-- com generation=update.

-- cirurgia_agendamento
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "dataPrevista";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "duracaoPrevistaMin";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "tipoAnestesia";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "necessitaUti";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "necessitaHemocomponente";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "reservaSangue";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "criadoEm";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "atualizadoEm";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "usuarioCriador";
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS "usuarioAtualizacao";

-- cirurgia_checklist
ALTER TABLE cirurgia_checklist DROP COLUMN IF EXISTS "respostasJson";
ALTER TABLE cirurgia_checklist DROP COLUMN IF EXISTS "registradoEm";
ALTER TABLE cirurgia_checklist DROP COLUMN IF EXISTS "usuarioRegistro";

-- cirurgia_contagem
ALTER TABLE cirurgia_contagem DROP COLUMN IF EXISTS "segundoConferente";
ALTER TABLE cirurgia_contagem DROP COLUMN IF EXISTS "registradoEm";
ALTER TABLE cirurgia_contagem DROP COLUMN IF EXISTS "usuarioRegistro";

-- cirurgia_descricao
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "sangramentoMl";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "materialAnatomiaPatologica";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "materialAnatomiaDescricao";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "condicaoSaida";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "destinoPos";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "assinadoEm";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "assinadoPor";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "criadoEm";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "atualizadoEm";
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS "usuarioRegistro";

-- cirurgia_equipe
ALTER TABLE cirurgia_equipe DROP COLUMN IF EXISTS "criadoEm";
ALTER TABLE cirurgia_equipe DROP COLUMN IF EXISTS "usuarioCriador";

-- cirurgia_ficha_anestesica
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "jejumHoras";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "viaAerea";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "acessoVenoso";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "drogasJson";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "sinaisJson";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "evolucaoRpa";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "criadoEm";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "atualizadoEm";
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS "usuarioRegistro";

-- cirurgia_lista_espera
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "procedimentoCodigo";
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "procedimentoDescricao";
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "justificativaPrioridade";
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "dataInclusao";
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "dataPrevista";
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "criadoEm";
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "atualizadoEm";
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS "usuarioCriador";

-- cirurgia_opme
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS "registroAnvisa";
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS "valorUnitario";
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS "criadoEm";
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS "usuarioRegistro";

-- cirurgia_procedimento
ALTER TABLE cirurgia_procedimento DROP COLUMN IF EXISTS "criadoEm";

-- cirurgia_sala
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS "horaInicioExpediente";
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS "horaFimExpediente";
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS "tempoLimpezaMin";
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS "criadoEm";
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS "usuarioCriador";

-- cirurgia_suspensao
ALTER TABLE cirurgia_suspensao DROP COLUMN IF EXISTS "suspensaEm";
ALTER TABLE cirurgia_suspensao DROP COLUMN IF EXISTS "idcirurgiaRemarcada";
ALTER TABLE cirurgia_suspensao DROP COLUMN IF EXISTS "usuarioRegistro";

-- cirurgia_tempo
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "entradaSala";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "inicioAnestesia";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "fimAnestesia";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "inicioCirurgia";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "fimCirurgia";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "saidaSala";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "inicioLimpeza";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "fimLimpeza";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "atualizadoEm";
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS "usuarioRegistro";

