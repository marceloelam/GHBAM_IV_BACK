CREATE TABLE IF NOT EXISTS classificacao_ghbam_auditoria (
    id BIGSERIAL PRIMARY KEY,
    item_uuid VARCHAR(80) NOT NULL,
    evento VARCHAR(40) NOT NULL,
    ator_nome VARCHAR(120),
    ator_matricula VARCHAR(40),
    detalhe TEXT,
    ocorrido_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_classificacao_ghbam_auditoria_item
    ON classificacao_ghbam_auditoria (item_uuid, ocorrido_em DESC);
