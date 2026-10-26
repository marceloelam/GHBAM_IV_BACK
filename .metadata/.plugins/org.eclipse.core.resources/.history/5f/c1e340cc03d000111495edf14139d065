-- Limites de dose do CDSS editaveis e validaveis no sistema.
-- O arquivo farmacia/base-conhecimento-farmaceutico.json continua sendo a base;
-- cdss_regra_dose guarda o valor vigente quando alterado e o estado de validacao.
-- O historico registra toda alteracao/validacao e nunca e apagado.

-- Permissao "Validar limites de dose (CDSS)" (pregrasdose): padrao FALSE para todos.
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS regrasdose BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS cdss_regra_dose (
    nome                    VARCHAR(120) PRIMARY KEY,
    alterado                BOOLEAN      NOT NULL DEFAULT FALSE,
    dose_maxima_dia_mg      NUMERIC(12,3),
    dose_maxima_mg_kg_dia   NUMERIC(12,3),
    pediatria_mg_kg_dia     NUMERIC(12,3),
    sem_teto_fixo           TEXT,
    idade_minima_anos       NUMERIC(6,2),
    validado                BOOLEAN      NOT NULL DEFAULT FALSE,
    validado_por            VARCHAR(120),
    validado_por_matricula  VARCHAR(40),
    validado_registro       VARCHAR(40),
    validado_em             TIMESTAMP,
    atualizado_por          VARCHAR(120),
    atualizado_por_matricula VARCHAR(40),
    atualizado_em           TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cdss_regra_dose_historico (
    id                  BIGSERIAL PRIMARY KEY,
    nome                VARCHAR(120) NOT NULL,
    acao                VARCHAR(20)  NOT NULL,
    detalhe             TEXT         NOT NULL,
    observacao          TEXT,
    por                 VARCHAR(120) NOT NULL,
    por_matricula       VARCHAR(40)  NOT NULL,
    registro_profissional VARCHAR(40),
    em                  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cdss_regra_dose_historico_nome ON cdss_regra_dose_historico (nome, em DESC);
