-- Remove de verdade as colunas duplicadas do centro cirurgico.
--
-- A V135 tentou a mesma limpeza escrevendo os nomes entre aspas ("criadoEm"),
-- que no Postgres significa identificador case-sensitive. Mas o Hibernate emitiu
-- o DDL sem aspas, e identificador nao-quotado e normalizado para minusculas na
-- criacao: a coluna real chama-se criadoem, nao "criadoEm". Os DROPs da V135
-- nao encontraram nada e passaram silenciosos por causa do IF EXISTS.
--
-- Sem esta limpeza o agendamento continua falhando pelo lado inverso: com as
-- entidades ja apontando para as colunas da V132 (criado_em), as duplicadas
-- NOT NULL nunca recebem valor e o insert viola a restricao.

-- cirurgia_agendamento
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS dataprevista;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS duracaoprevistamin;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS tipoanestesia;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS necessitauti;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS necessitahemocomponente;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS reservasangue;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS criadoem;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS atualizadoem;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS usuariocriador;
ALTER TABLE cirurgia_agendamento DROP COLUMN IF EXISTS usuarioatualizacao;

-- cirurgia_checklist
ALTER TABLE cirurgia_checklist DROP COLUMN IF EXISTS respostasjson;
ALTER TABLE cirurgia_checklist DROP COLUMN IF EXISTS registradoem;
ALTER TABLE cirurgia_checklist DROP COLUMN IF EXISTS usuarioregistro;

-- cirurgia_contagem
ALTER TABLE cirurgia_contagem DROP COLUMN IF EXISTS segundoconferente;
ALTER TABLE cirurgia_contagem DROP COLUMN IF EXISTS registradoem;
ALTER TABLE cirurgia_contagem DROP COLUMN IF EXISTS usuarioregistro;

-- cirurgia_descricao
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS sangramentoml;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS materialanatomiapatologica;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS materialanatomiadescricao;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS condicaosaida;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS destinopos;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS assinadoem;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS assinadopor;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS criadoem;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS atualizadoem;
ALTER TABLE cirurgia_descricao DROP COLUMN IF EXISTS usuarioregistro;

-- cirurgia_equipe
ALTER TABLE cirurgia_equipe DROP COLUMN IF EXISTS criadoem;
ALTER TABLE cirurgia_equipe DROP COLUMN IF EXISTS usuariocriador;

-- cirurgia_ficha_anestesica
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS jejumhoras;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS viaaerea;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS acessovenoso;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS drogasjson;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS sinaisjson;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS evolucaorpa;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS criadoem;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS atualizadoem;
ALTER TABLE cirurgia_ficha_anestesica DROP COLUMN IF EXISTS usuarioregistro;

-- cirurgia_lista_espera
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS procedimentocodigo;
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS procedimentodescricao;
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS justificativaprioridade;
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS datainclusao;
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS dataprevista;
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS criadoem;
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS atualizadoem;
ALTER TABLE cirurgia_lista_espera DROP COLUMN IF EXISTS usuariocriador;

-- cirurgia_opme
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS registroanvisa;
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS valorunitario;
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS criadoem;
ALTER TABLE cirurgia_opme DROP COLUMN IF EXISTS usuarioregistro;

-- cirurgia_procedimento
ALTER TABLE cirurgia_procedimento DROP COLUMN IF EXISTS criadoem;

-- cirurgia_sala
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS horainicioexpediente;
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS horafimexpediente;
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS tempolimpezamin;
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS criadoem;
ALTER TABLE cirurgia_sala DROP COLUMN IF EXISTS usuariocriador;

-- cirurgia_suspensao
ALTER TABLE cirurgia_suspensao DROP COLUMN IF EXISTS suspensaem;
ALTER TABLE cirurgia_suspensao DROP COLUMN IF EXISTS idcirurgiaremarcada;
ALTER TABLE cirurgia_suspensao DROP COLUMN IF EXISTS usuarioregistro;

-- cirurgia_tempo
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS entradasala;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS inicioanestesia;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS fimanestesia;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS iniciocirurgia;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS fimcirurgia;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS saidasala;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS iniciolimpeza;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS fimlimpeza;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS atualizadoem;
ALTER TABLE cirurgia_tempo DROP COLUMN IF EXISTS usuarioregistro;

