-- Pendência de cadastro profissional (COREN/CRM igual ao de outra pessoa, enfermagem sem COREN).
-- Não trava cadastro nem login: abre prazo de 30 dias; vencida, o alerta escala para os líderes
-- e os documentos deixam de imprimir o número. Uma aberta por servidor; resolvidas ficam de histórico.
CREATE TABLE IF NOT EXISTS servidor_pendencia_cadastro (
    id            BIGSERIAL PRIMARY KEY,
    idservidor    INTEGER      NOT NULL,
    matricula     VARCHAR(30),
    unidade       VARCHAR(60),
    motivo        TEXT         NOT NULL,
    aberta_em     TIMESTAMP    NOT NULL DEFAULT now(),
    prazo         DATE         NOT NULL,
    resolvida_em  TIMESTAMP,
    resolvida_por VARCHAR(30)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_servidor_pendencia_cadastro_aberta
    ON servidor_pendencia_cadastro (idservidor) WHERE resolvida_em IS NULL;

CREATE INDEX IF NOT EXISTS ix_servidor_pendencia_cadastro_unidade_aberta
    ON servidor_pendencia_cadastro (unidade) WHERE resolvida_em IS NULL;
