ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS resumoclinicoia BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS resumo_clinico_auditoria (
    id BIGSERIAL PRIMARY KEY,
    paciente_id INTEGER NOT NULL,
    bam INTEGER NOT NULL,
    usuario VARCHAR(40) NOT NULL,
    usuario_nome VARCHAR(120),
    tipo_resumo VARCHAR(20) NOT NULL,
    periodo VARCHAR(30) NOT NULL,
    gerado_em TIMESTAMP NOT NULL,
    modelo VARCHAR(80),
    modo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tempo_processamento_ms BIGINT,
    tokens_entrada INTEGER,
    tokens_saida INTEGER,
    hash_contexto VARCHAR(64),
    fontes_consultadas TEXT,
    erro VARCHAR(240),
    ip_usuario VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_resumo_clinico_auditoria_bam_data
    ON resumo_clinico_auditoria (bam, gerado_em DESC);
CREATE INDEX IF NOT EXISTS idx_resumo_clinico_auditoria_paciente
    ON resumo_clinico_auditoria (paciente_id);
