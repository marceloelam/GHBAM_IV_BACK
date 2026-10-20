-- Um laudo laboratorial tem varios exames; cada item vira um Observation no envio RNDS.
-- codigo_loinc nulo = exame nao reconhecido pelo dicionario: bloqueia o envio (pendencia).
CREATE TABLE IF NOT EXISTS pedidoresultadolaboratorio_item (
    iditem              BIGSERIAL PRIMARY KEY,
    idpedidoresultado   INTEGER      NOT NULL,
    nome_exame          VARCHAR(120) NOT NULL,
    codigo_loinc        VARCHAR(30),
    valor               VARCHAR(60)  NOT NULL,
    unidade_medida      VARCHAR(50),
    referencia_valor    VARCHAR(120),
    ordem               INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_resultado_lab_item_resultado
    ON pedidoresultadolaboratorio_item (idpedidoresultado);
