-- Passagem de plantao de enfermagem por leito (SBAR): quem entregou registra,
-- quem assume confirma o recebimento. Registros nao sao apagados nem editados;
-- uma nova passagem cria uma nova linha.
CREATE TABLE IF NOT EXISTS passagem_plantao (
    id                      BIGSERIAL PRIMARY KEY,
    unidade                 VARCHAR(25)  NOT NULL,
    idbam                   INTEGER      NOT NULL,
    idpaciente              INTEGER,
    situacao                TEXT         NOT NULL,
    historico               TEXT,
    avaliacao               TEXT,
    recomendacao            TEXT,
    entregue_por            VARCHAR(120) NOT NULL,
    entregue_por_matricula  VARCHAR(40)  NOT NULL,
    entregue_em             TIMESTAMP    NOT NULL DEFAULT now(),
    recebido_por            VARCHAR(120),
    recebido_por_matricula  VARCHAR(40),
    recebido_em             TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_passagem_plantao_bam ON passagem_plantao (idbam, entregue_em DESC);
CREATE INDEX IF NOT EXISTS idx_passagem_plantao_unidade ON passagem_plantao (unidade, entregue_em DESC);
