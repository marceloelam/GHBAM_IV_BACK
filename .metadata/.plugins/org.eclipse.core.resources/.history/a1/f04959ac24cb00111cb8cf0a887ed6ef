-- GHBAM APS: nucleo do modulo de Atencao Primaria (paridade com o PEC e-SUS APS).
-- Referencia: docs/aps/PLANO_MODULO_APS.md (frontend) e LEDI APS 8.7.0.
-- Somente tabelas novas; nenhuma tabela do hospital e alterada. O cidadao APS
-- se liga ao cadastro unico do hospital por aps_cidadao.id_paciente.
-- As tabelas das V165..V169 (aps_cadastro_territorial, aps_visita_domiciliar,
-- aps_equipe_microarea, aps_atendimento_individual) ficam preservadas e sem uso.

-- Parametros da instalacao (remetente/originadora do LEDI) - linha unica 'GLOBAL'.
CREATE TABLE IF NOT EXISTS aps_parametro (
    chave               VARCHAR(30) PRIMARY KEY,
    contra_chave        VARCHAR(120) NOT NULL DEFAULT 'GHBAM APS - 4.0',
    uuid_instalacao     VARCHAR(64)  NOT NULL,
    cpf_cnpj            VARCHAR(18),
    nome_razao_social   VARCHAR(160),
    fone                VARCHAR(30),
    email               VARCHAR(120),
    cod_ibge            VARCHAR(7),
    envio_automatico    BOOLEAN NOT NULL DEFAULT FALSE,
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_por      VARCHAR(20)
);
INSERT INTO aps_parametro (chave, uuid_instalacao)
SELECT 'GLOBAL', md5(random()::text || clock_timestamp()::text)
WHERE NOT EXISTS (SELECT 1 FROM aps_parametro WHERE chave = 'GLOBAL');

-- Equipes (INE) por unidade (sigla GHBAM) e CNES.
CREATE TABLE IF NOT EXISTS aps_equipe (
    id           BIGSERIAL PRIMARY KEY,
    unidade      VARCHAR(30)  NOT NULL,
    cnes         VARCHAR(7)   NOT NULL,
    ine          VARCHAR(10)  NOT NULL,
    nome         VARCHAR(120) NOT NULL,
    tipo         VARCHAR(20)  NOT NULL DEFAULT 'ESF',
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_aps_equipe_ine UNIQUE (ine)
);
CREATE INDEX IF NOT EXISTS idx_aps_equipe_unidade ON aps_equipe (unidade);

-- Microareas (2 digitos) da equipe, com o ACS responsavel.
CREATE TABLE IF NOT EXISTS aps_microarea (
    id              BIGSERIAL PRIMARY KEY,
    id_equipe       BIGINT      NOT NULL REFERENCES aps_equipe (id),
    codigo          VARCHAR(2)  NOT NULL,
    descricao       VARCHAR(160),
    matricula_acs   VARCHAR(20),
    nome_acs        VARCHAR(120),
    ativo           BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_aps_microarea UNIQUE (id_equipe, codigo)
);

-- Lotacao do profissional (CNS + CBO + CNES + INE) usada no header LEDI.
CREATE TABLE IF NOT EXISTS aps_lotacao (
    id            BIGSERIAL PRIMARY KEY,
    matricula     VARCHAR(20)  NOT NULL,
    nome          VARCHAR(160) NOT NULL,
    cns           VARCHAR(15),
    cpf           VARCHAR(11),
    cbo           VARCHAR(6)   NOT NULL,
    unidade       VARCHAR(30)  NOT NULL,
    cnes          VARCHAR(7)   NOT NULL,
    id_equipe     BIGINT REFERENCES aps_equipe (id),
    ine           VARCHAR(10),
    estagiario    BOOLEAN      NOT NULL DEFAULT FALSE,
    matricula_supervisor VARCHAR(20),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_aps_lotacao_matricula ON aps_lotacao (matricula, ativo);
CREATE INDEX IF NOT EXISTS idx_aps_lotacao_unidade ON aps_lotacao (unidade, ativo);

-- Domicilio / imovel (Cadastro Domiciliar e Territorial).
CREATE TABLE IF NOT EXISTS aps_domicilio (
    id                  BIGSERIAL PRIMARY KEY,
    unidade             VARCHAR(30) NOT NULL,
    id_equipe           BIGINT REFERENCES aps_equipe (id),
    microarea           VARCHAR(2),
    fora_area           BOOLEAN NOT NULL DEFAULT FALSE,
    tipo_imovel         INTEGER NOT NULL DEFAULT 1,
    cep                 VARCHAR(8),
    cod_ibge            VARCHAR(7),
    uf_dne              VARCHAR(2),
    tipo_logradouro_dne VARCHAR(3),
    logradouro          VARCHAR(160),
    numero              VARCHAR(10),
    sem_numero          BOOLEAN NOT NULL DEFAULT FALSE,
    complemento         VARCHAR(60),
    bairro              VARCHAR(80),
    ponto_referencia    VARCHAR(80),
    telefone_residencia VARCHAR(20),
    telefone_contato    VARCHAR(20),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    dados               JSONB NOT NULL DEFAULT '{}'::jsonb,
    recusa              BOOLEAN NOT NULL DEFAULT FALSE,
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    uuid_ficha_origem   VARCHAR(44),
    uuid_ultima_ficha   VARCHAR(44),
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por          VARCHAR(20),
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_por      VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_aps_domicilio_territorio ON aps_domicilio (unidade, id_equipe, microarea);
CREATE INDEX IF NOT EXISTS idx_aps_domicilio_cep ON aps_domicilio (cep);

-- Familia dentro do domicilio.
CREATE TABLE IF NOT EXISTS aps_familia (
    id                          BIGSERIAL PRIMARY KEY,
    id_domicilio                BIGINT NOT NULL REFERENCES aps_domicilio (id),
    id_cidadao_responsavel      BIGINT,
    cpf_responsavel             VARCHAR(11),
    cns_responsavel             VARCHAR(15),
    data_nascimento_responsavel DATE,
    numero_prontuario           VARCHAR(30),
    numero_membros              INTEGER,
    renda_familiar              INTEGER,
    reside_desde                DATE,
    mudou                       BOOLEAN NOT NULL DEFAULT FALSE,
    ativo                       BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_aps_familia_domicilio ON aps_familia (id_domicilio);

-- Cidadao da APS (Cadastro Individual). Liga ao paciente unico do hospital.
CREATE TABLE IF NOT EXISTS aps_cidadao (
    id                  BIGSERIAL PRIMARY KEY,
    id_paciente         INTEGER,
    cpf                 VARCHAR(11),
    cns                 VARCHAR(15),
    nome                VARCHAR(160) NOT NULL,
    nome_social         VARCHAR(160),
    data_nascimento     DATE,
    sexo                INTEGER,
    raca_cor            INTEGER,
    nome_mae            VARCHAR(160),
    telefone            VARCHAR(20),
    unidade             VARCHAR(30) NOT NULL,
    id_equipe           BIGINT REFERENCES aps_equipe (id),
    microarea           VARCHAR(2),
    fora_area           BOOLEAN NOT NULL DEFAULT FALSE,
    id_familia          BIGINT REFERENCES aps_familia (id),
    responsavel_familiar BOOLEAN NOT NULL DEFAULT FALSE,
    cpf_responsavel     VARCHAR(11),
    cns_responsavel     VARCHAR(15),
    dados               JSONB NOT NULL DEFAULT '{}'::jsonb,
    recusa              BOOLEAN NOT NULL DEFAULT FALSE,
    saida_motivo        INTEGER,
    saida_data          DATE,
    numero_do           VARCHAR(20),
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    uuid_ficha_origem   VARCHAR(44),
    uuid_ultima_ficha   VARCHAR(44),
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por          VARCHAR(20),
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_por      VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_aps_cidadao_cpf ON aps_cidadao (cpf);
CREATE INDEX IF NOT EXISTS idx_aps_cidadao_cns ON aps_cidadao (cns);
CREATE INDEX IF NOT EXISTS idx_aps_cidadao_paciente ON aps_cidadao (id_paciente);
CREATE INDEX IF NOT EXISTS idx_aps_cidadao_territorio ON aps_cidadao (unidade, id_equipe, microarea);
CREATE INDEX IF NOT EXISTS idx_aps_cidadao_nome ON aps_cidadao (UPPER(nome));

-- Registro LEDI (um por modelo de informacao/ficha). E a fonte da transmissao.
CREATE TABLE IF NOT EXISTS aps_ficha (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                VARCHAR(44) NOT NULL,
    uuid_origem         VARCHAR(44),
    tipo                INTEGER NOT NULL,
    unidade             VARCHAR(30) NOT NULL,
    cnes                VARCHAR(7)  NOT NULL,
    ine                 VARCHAR(10),
    cod_ibge            VARCHAR(7),
    matricula           VARCHAR(20),
    profissional_nome   VARCHAR(160),
    profissional_cns    VARCHAR(15),
    profissional_cbo    VARCHAR(6),
    data_atendimento    DATE NOT NULL,
    id_cidadao          BIGINT,
    id_domicilio        BIGINT,
    id_atendimento      BIGINT,
    origem              VARCHAR(30) NOT NULL DEFAULT 'CDS',
    dados               JSONB NOT NULL DEFAULT '{}'::jsonb,
    status              VARCHAR(20) NOT NULL DEFAULT 'PRONTA',
    versao_ledi         VARCHAR(10) NOT NULL DEFAULT '8.7.0',
    tentativas          INTEGER NOT NULL DEFAULT 0,
    ultimo_erro         TEXT,
    lote                BIGINT,
    enviado_em          TIMESTAMP,
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por          VARCHAR(20),
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_aps_ficha_uuid UNIQUE (uuid),
    CONSTRAINT ck_aps_ficha_status CHECK (status IN ('RASCUNHO', 'PRONTA', 'ENVIADA', 'REJEITADA', 'CANCELADA'))
);
CREATE INDEX IF NOT EXISTS idx_aps_ficha_status ON aps_ficha (status);
CREATE INDEX IF NOT EXISTS idx_aps_ficha_tipo_data ON aps_ficha (tipo, data_atendimento);
CREATE INDEX IF NOT EXISTS idx_aps_ficha_unidade_data ON aps_ficha (unidade, data_atendimento);
CREATE INDEX IF NOT EXISTS idx_aps_ficha_cidadao ON aps_ficha (id_cidadao);

CREATE TABLE IF NOT EXISTS aps_transmissao_log (
    id            BIGSERIAL PRIMARY KEY,
    id_ficha      BIGINT NOT NULL REFERENCES aps_ficha (id),
    data_hora     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sucesso       BOOLEAN NOT NULL,
    http_status   INTEGER,
    mensagem      TEXT,
    lote          BIGINT,
    matricula     VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_aps_transmissao_ficha ON aps_transmissao_log (id_ficha);

-- Lista de atendimentos do dia (demanda espontanea e agendada) + escuta inicial.
CREATE TABLE IF NOT EXISTS aps_lista_atendimento (
    id                  BIGSERIAL PRIMARY KEY,
    unidade             VARCHAR(30) NOT NULL,
    data                DATE NOT NULL,
    id_cidadao          BIGINT NOT NULL REFERENCES aps_cidadao (id),
    tipo_demanda        VARCHAR(20) NOT NULL DEFAULT 'ESPONTANEA',
    id_agendamento      BIGINT,
    servicos            VARCHAR(300),
    matricula_destino   VARCHAR(20),
    profissional_destino VARCHAR(160),
    id_equipe           BIGINT,
    status              VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO',
    classificacao_risco INTEGER,
    hora_chegada        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hora_inicio         TIMESTAMP,
    hora_fim            TIMESTAMP,
    id_atendimento      BIGINT,
    escuta              JSONB,
    observacao          VARCHAR(500),
    criado_por          VARCHAR(20),
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_lista_status CHECK (status IN ('AGUARDANDO', 'EM_ESCUTA', 'AGUARDANDO_ATENDIMENTO',
        'EM_ATENDIMENTO', 'EM_OBSERVACAO', 'ATENDIDO', 'NAO_AGUARDOU', 'CANCELADO'))
);
CREATE INDEX IF NOT EXISTS idx_aps_lista_unidade_data ON aps_lista_atendimento (unidade, data);

-- Atendimento clinico (SOAP e especificos). Ao finalizar gera aps_ficha(s).
CREATE TABLE IF NOT EXISTS aps_atendimento (
    id                  BIGSERIAL PRIMARY KEY,
    tipo                VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    unidade             VARCHAR(30) NOT NULL,
    cnes                VARCHAR(7),
    ine                 VARCHAR(10),
    id_lista            BIGINT,
    id_cidadao          BIGINT NOT NULL REFERENCES aps_cidadao (id),
    matricula           VARCHAR(20) NOT NULL,
    profissional        VARCHAR(160),
    cns_profissional    VARCHAR(15),
    cbo                 VARCHAR(6),
    matricula_supervisor VARCHAR(20),
    inicio              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fim                 TIMESTAMP,
    status              VARCHAR(20) NOT NULL DEFAULT 'EM_ANDAMENTO',
    registro_tardio     BOOLEAN NOT NULL DEFAULT FALSE,
    data_referencia     DATE NOT NULL DEFAULT CURRENT_DATE,
    soap                JSONB NOT NULL DEFAULT '{}'::jsonb,
    dados               JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_atendimento_status CHECK (status IN ('EM_ANDAMENTO', 'FINALIZADO', 'CANCELADO'))
);
CREATE INDEX IF NOT EXISTS idx_aps_atend_cidadao ON aps_atendimento (id_cidadao, inicio DESC);
CREATE INDEX IF NOT EXISTS idx_aps_atend_unidade ON aps_atendimento (unidade, data_referencia);

-- Lista de problemas/condicoes e alergias (registro clinico orientado por problemas).
CREATE TABLE IF NOT EXISTS aps_problema (
    id              BIGSERIAL PRIMARY KEY,
    uuid            VARCHAR(44) NOT NULL,
    id_cidadao      BIGINT NOT NULL REFERENCES aps_cidadao (id),
    tipo            VARCHAR(10) NOT NULL DEFAULT 'PROBLEMA',
    ciap            VARCHAR(10),
    cid10           VARCHAR(5),
    descricao       VARCHAR(300),
    situacao        INTEGER NOT NULL DEFAULT 0,
    data_inicio     DATE,
    data_fim        DATE,
    categoria       VARCHAR(40),
    criticidade     VARCHAR(20),
    reacao          VARCHAR(300),
    observacao      VARCHAR(500),
    id_atendimento  BIGINT,
    criado_por      VARCHAR(20),
    criado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_problema_tipo CHECK (tipo IN ('PROBLEMA', 'ALERGIA'))
);
CREATE INDEX IF NOT EXISTS idx_aps_problema_cidadao ON aps_problema (id_cidadao);

-- Medicoes (series temporais: curvas de crescimento, gestacao, PA, glicemia).
CREATE TABLE IF NOT EXISTS aps_medicao (
    id                      BIGSERIAL PRIMARY KEY,
    id_cidadao              BIGINT NOT NULL REFERENCES aps_cidadao (id),
    id_atendimento          BIGINT,
    data_hora               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    peso                    NUMERIC(6,3),
    altura                  NUMERIC(5,1),
    imc                     NUMERIC(5,2),
    pressao_sistolica       INTEGER,
    pressao_diastolica      INTEGER,
    frequencia_cardiaca     INTEGER,
    frequencia_respiratoria INTEGER,
    temperatura             NUMERIC(4,1),
    saturacao_o2            INTEGER,
    glicemia                INTEGER,
    tipo_glicemia           INTEGER,
    perimetro_cefalico      NUMERIC(5,1),
    circunferencia_abdominal NUMERIC(5,1),
    perimetro_panturrilha   NUMERIC(5,1),
    registrado_por          VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_aps_medicao_cidadao ON aps_medicao (id_cidadao, data_hora);

-- Pre-natal / puerperio.
CREATE TABLE IF NOT EXISTS aps_gestacao (
    id                  BIGSERIAL PRIMARY KEY,
    id_cidadao          BIGINT NOT NULL REFERENCES aps_cidadao (id),
    dum                 DATE,
    dpp                 DATE,
    gravidez_planejada  BOOLEAN,
    gestas_previas      INTEGER,
    partos              INTEGER,
    alto_risco          BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    desfecho            VARCHAR(40),
    data_desfecho       DATE,
    dados               JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_gestacao_status CHECK (status IN ('ATIVA', 'PUERPERIO', 'ENCERRADA'))
);
CREATE INDEX IF NOT EXISTS idx_aps_gestacao_cidadao ON aps_gestacao (id_cidadao, status);

-- Lotes de imunobiologicos por unidade.
CREATE TABLE IF NOT EXISTS aps_lote_imunobiologico (
    id                  BIGSERIAL PRIMARY KEY,
    unidade             VARCHAR(30) NOT NULL,
    imunobiologico      INTEGER NOT NULL,
    lote                VARCHAR(30) NOT NULL,
    fabricante          VARCHAR(120),
    co_rnds_fabricante  VARCHAR(10),
    validade            DATE NOT NULL,
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_aps_lote UNIQUE (unidade, imunobiologico, lote)
);

-- Doses aplicadas / transcritas / aprazadas (caderneta).
CREATE TABLE IF NOT EXISTS aps_vacinacao (
    id                      BIGSERIAL PRIMARY KEY,
    id_cidadao              BIGINT NOT NULL REFERENCES aps_cidadao (id),
    id_atendimento          BIGINT,
    unidade                 VARCHAR(30) NOT NULL,
    tipo_registro           VARCHAR(20) NOT NULL DEFAULT 'APLICACAO',
    data_aplicacao          DATE,
    data_aprazamento        DATE,
    imunobiologico          INTEGER NOT NULL,
    estrategia              INTEGER,
    dose                    INTEGER,
    lote                    VARCHAR(30),
    fabricante              VARCHAR(120),
    co_rnds_fabricante      VARCHAR(10),
    via_administracao       INTEGER,
    local_aplicacao         INTEGER,
    grupo_atendimento       INTEGER,
    aplicado_exterior       BOOLEAN NOT NULL DEFAULT FALSE,
    gestante                BOOLEAN NOT NULL DEFAULT FALSE,
    puerpera                BOOLEAN NOT NULL DEFAULT FALSE,
    viajante                BOOLEAN NOT NULL DEFAULT FALSE,
    comunicante_hanseniase  BOOLEAN NOT NULL DEFAULT FALSE,
    matricula               VARCHAR(20),
    profissional            VARCHAR(160),
    cns_profissional        VARCHAR(15),
    cbo                     VARCHAR(6),
    observacao              VARCHAR(500),
    id_ficha                BIGINT,
    excluido                BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_exclusao         VARCHAR(300),
    criado_em               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_vacinacao_tipo CHECK (tipo_registro IN ('APLICACAO', 'TRANSCRICAO', 'APRAZAMENTO'))
);
CREATE INDEX IF NOT EXISTS idx_aps_vacinacao_cidadao ON aps_vacinacao (id_cidadao, excluido);
CREATE INDEX IF NOT EXISTS idx_aps_vacinacao_unidade ON aps_vacinacao (unidade, data_aplicacao);

-- Agenda do profissional: grade semanal e fechamentos.
CREATE TABLE IF NOT EXISTS aps_agenda_config (
    id                  BIGSERIAL PRIMARY KEY,
    matricula           VARCHAR(20) NOT NULL,
    unidade             VARCHAR(30) NOT NULL,
    dia_semana          INTEGER NOT NULL,
    hora_inicio         TIME NOT NULL,
    hora_fim            TIME NOT NULL,
    duracao_minutos     INTEGER NOT NULL DEFAULT 20,
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_aps_agenda_dia CHECK (dia_semana BETWEEN 1 AND 7)
);
CREATE INDEX IF NOT EXISTS idx_aps_agenda_config ON aps_agenda_config (matricula, unidade);

CREATE TABLE IF NOT EXISTS aps_agenda_fechamento (
    id              BIGSERIAL PRIMARY KEY,
    matricula       VARCHAR(20) NOT NULL,
    unidade         VARCHAR(30) NOT NULL,
    data_inicio     DATE NOT NULL,
    data_fim        DATE NOT NULL,
    motivo          VARCHAR(120) NOT NULL,
    criado_por      VARCHAR(20),
    criado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Agendamentos: reaproveita aps_agendamento (V170), estendendo-a.
ALTER TABLE aps_agendamento ALTER COLUMN microarea DROP NOT NULL;
ALTER TABLE aps_agendamento ADD COLUMN IF NOT EXISTS id_cidadao BIGINT;
ALTER TABLE aps_agendamento ADD COLUMN IF NOT EXISTS cbo VARCHAR(6);
ALTER TABLE aps_agendamento ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'CONSULTA';
ALTER TABLE aps_agendamento ADD COLUMN IF NOT EXISTS local VARCHAR(20) NOT NULL DEFAULT 'UBS';
ALTER TABLE aps_agendamento ADD COLUMN IF NOT EXISTS pre_natal BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE aps_agendamento ADD COLUMN IF NOT EXISTS agendado_por VARCHAR(20);
ALTER TABLE aps_agendamento ADD COLUMN IF NOT EXISTS email_enviado BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_aps_agendamento_cidadao ON aps_agendamento (id_cidadao);

-- Garantia do acesso (fila de demanda reprimida).
CREATE TABLE IF NOT EXISTS aps_garantia_acesso (
    id              BIGSERIAL PRIMARY KEY,
    unidade         VARCHAR(30) NOT NULL,
    id_cidadao      BIGINT NOT NULL REFERENCES aps_cidadao (id),
    id_equipe       BIGINT,
    tipo_servico    VARCHAR(60) NOT NULL,
    motivo          VARCHAR(300),
    status          VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    motivo_saida    VARCHAR(120),
    criado_por      VARCHAR(20),
    criado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolvido_em    TIMESTAMP,
    CONSTRAINT ck_aps_garantia_status CHECK (status IN ('ABERTO', 'AGENDADO', 'REMOVIDO'))
);
CREATE INDEX IF NOT EXISTS idx_aps_garantia_unidade ON aps_garantia_acesso (unidade, status);

-- Cuidado compartilhado (discussao de caso entre profissionais).
CREATE TABLE IF NOT EXISTS aps_cuidado_compartilhado (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                VARCHAR(44) NOT NULL,
    unidade             VARCHAR(30) NOT NULL,
    id_cidadao          BIGINT NOT NULL REFERENCES aps_cidadao (id),
    id_atendimento      BIGINT,
    matricula_solicitante VARCHAR(20) NOT NULL,
    nome_solicitante    VARCHAR(160),
    cbo_solicitante     VARCHAR(6),
    cns_solicitante     VARCHAR(15),
    ine_solicitante     VARCHAR(10),
    matricula_executante VARCHAR(20),
    nome_executante     VARCHAR(160),
    cbo_executante      VARCHAR(6),
    cns_executante      VARCHAR(15),
    ine_executante      VARCHAR(10),
    ciap                VARCHAR(10),
    cid10               VARCHAR(5),
    prioridade          INTEGER NOT NULL DEFAULT 2,
    discussao           TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'AGUARDANDO',
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_aps_cuidado_unidade ON aps_cuidado_compartilhado (unidade, status);

CREATE TABLE IF NOT EXISTS aps_cuidado_evolucao (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                VARCHAR(44) NOT NULL,
    id_cuidado          BIGINT NOT NULL REFERENCES aps_cuidado_compartilhado (id),
    sequencial          INTEGER NOT NULL,
    matricula           VARCHAR(20) NOT NULL,
    nome                VARCHAR(160),
    cbo                 VARCHAR(6),
    conduta             INTEGER NOT NULL,
    texto               TEXT,
    id_ficha            BIGINT,
    criado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_aps_cuidado_evolucao ON aps_cuidado_evolucao (id_cuidado, sequencial);
