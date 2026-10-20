-- Dispositivos invasivos por internacao (CCIH: dispositivos-dia e bundles).
-- Insercao cria a linha; a retirada so preenche retirado_*. Nada e apagado.
CREATE TABLE IF NOT EXISTS dispositivo_invasivo (
    id                      BIGSERIAL PRIMARY KEY,
    unidade                 VARCHAR(25)  NOT NULL,
    idbam                   INTEGER      NOT NULL,
    idpaciente              INTEGER,
    tipo                    VARCHAR(30)  NOT NULL,
    sitio                   VARCHAR(80),
    inserido_em             TIMESTAMP    NOT NULL,
    inserido_por            VARCHAR(120) NOT NULL,
    inserido_por_matricula  VARCHAR(40)  NOT NULL,
    registrado_em           TIMESTAMP    NOT NULL DEFAULT now(),
    retirado_em             TIMESTAMP,
    retirado_por            VARCHAR(120),
    retirado_por_matricula  VARCHAR(40),
    motivo_retirada         VARCHAR(200)
);

CREATE INDEX IF NOT EXISTS idx_dispositivo_invasivo_bam ON dispositivo_invasivo (idbam);
CREATE INDEX IF NOT EXISTS idx_dispositivo_invasivo_ativos
    ON dispositivo_invasivo (unidade, tipo) WHERE retirado_em IS NULL;
