CREATE TABLE IF NOT EXISTS cdss_preventivo_cache (
    id BIGSERIAL PRIMARY KEY,
    idpaciente INTEGER NOT NULL,
    unidade VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OK',
    total_alertas INTEGER NOT NULL DEFAULT 0,
    total_alta INTEGER NOT NULL DEFAULT 0,
    avaliado_em TIMESTAMP NOT NULL,
    payload TEXT NOT NULL,
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP,
    CONSTRAINT uk_cdss_preventivo_cache_paciente_unidade UNIQUE (idpaciente, unidade)
);

CREATE INDEX IF NOT EXISTS idx_cdss_preventivo_cache_unidade_avaliado
    ON cdss_preventivo_cache (unidade, avaliado_em DESC);

CREATE INDEX IF NOT EXISTS idx_cdss_preventivo_cache_unidade_alertas
    ON cdss_preventivo_cache (unidade, total_alertas DESC, total_alta DESC);
