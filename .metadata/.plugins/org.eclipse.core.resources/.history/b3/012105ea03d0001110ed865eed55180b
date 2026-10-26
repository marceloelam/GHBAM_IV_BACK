-- Sugestoes de alteracao de limite de dose feitas por qualquer clinico.
-- Nao alteram nada sozinhas: quem tem pregrasdose aprova (com CRF/CRM) ou rejeita
-- (com motivo). Nada e apagado.
CREATE TABLE IF NOT EXISTS cdss_regra_dose_sugestao (
    id                      BIGSERIAL PRIMARY KEY,
    nome                    VARCHAR(120) NOT NULL,
    dose_maxima_dia_mg      NUMERIC(12,3),
    dose_maxima_mg_kg_dia   NUMERIC(12,3),
    pediatria_mg_kg_dia     NUMERIC(12,3),
    sem_teto_fixo           TEXT,
    idade_minima_anos       NUMERIC(6,2),
    mudancas                TEXT         NOT NULL,
    justificativa           TEXT         NOT NULL,
    referencia              TEXT,
    status                  VARCHAR(10)  NOT NULL DEFAULT 'PENDENTE',
    sugerido_por            VARCHAR(120) NOT NULL,
    sugerido_por_matricula  VARCHAR(40)  NOT NULL,
    sugerido_unidade        VARCHAR(25),
    sugerido_em             TIMESTAMP    NOT NULL DEFAULT now(),
    decidido_por            VARCHAR(120),
    decidido_por_matricula  VARCHAR(40),
    decidido_registro       VARCHAR(40),
    decidido_em             TIMESTAMP,
    motivo_decisao          TEXT
);

CREATE INDEX IF NOT EXISTS idx_cdss_regra_dose_sugestao_status ON cdss_regra_dose_sugestao (status, sugerido_em DESC);
