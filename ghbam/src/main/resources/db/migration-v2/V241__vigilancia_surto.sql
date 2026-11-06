-- Registro formal de surto por unidade. Sem FK na regra: excluir a regra nao pode apagar a investigacao.
CREATE TABLE IF NOT EXISTS vigilancia_surto (
    id            BIGSERIAL PRIMARY KEY,
    unidade       VARCHAR(60)  NOT NULL,
    regra_id      INTEGER      NOT NULL,
    doenca        VARCHAR(120) NOT NULL,
    titulo        VARCHAR(160) NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('EM_INVESTIGACAO', 'CONFIRMADO', 'DESCARTADO', 'ENCERRADO')),
    justificativa TEXT         NOT NULL,
    matricula     VARCHAR(30)  NOT NULL,
    profissional  VARCHAR(200) NOT NULL DEFAULT '',
    aberto_em     TIMESTAMP    NOT NULL DEFAULT now(),
    concluido_em  TIMESTAMP
);

-- Uma investigacao ativa por regra, tambem sob cliques simultaneos.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vigilancia_surto_ativo
    ON vigilancia_surto (unidade, regra_id) WHERE status IN ('EM_INVESTIGACAO', 'CONFIRMADO');
CREATE INDEX IF NOT EXISTS idx_vigilancia_surto_unidade ON vigilancia_surto (unidade, id DESC);

-- Vinculo removido fica marcado em removido_em, nunca apagado.
CREATE TABLE IF NOT EXISTS vigilancia_surto_caso (
    id           BIGSERIAL PRIMARY KEY,
    surto_id     BIGINT       NOT NULL REFERENCES vigilancia_surto (id),
    idbam        INTEGER      NOT NULL,
    idpaciente   INTEGER,
    matricula    VARCHAR(30)  NOT NULL,
    profissional VARCHAR(200) NOT NULL DEFAULT '',
    criado_em    TIMESTAMP    NOT NULL DEFAULT now(),
    removido_em  TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_vigilancia_surto_caso_ativo
    ON vigilancia_surto_caso (surto_id, idbam) WHERE removido_em IS NULL;

-- Historico: so recebe INSERT.
CREATE TABLE IF NOT EXISTS vigilancia_surto_evento (
    id           BIGSERIAL PRIMARY KEY,
    surto_id     BIGINT       NOT NULL REFERENCES vigilancia_surto (id),
    tipo         VARCHAR(30)  NOT NULL,
    descricao    TEXT         NOT NULL,
    matricula    VARCHAR(30)  NOT NULL,
    profissional VARCHAR(200) NOT NULL DEFAULT '',
    criado_em    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vigilancia_surto_evento ON vigilancia_surto_evento (surto_id, id DESC);
