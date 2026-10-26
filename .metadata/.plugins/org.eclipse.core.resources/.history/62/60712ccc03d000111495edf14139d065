CREATE TABLE IF NOT EXISTS unidade_videos (
    id SERIAL PRIMARY KEY,
    sigla VARCHAR(15) NOT NULL,
    titulo VARCHAR(120),
    url VARCHAR(500) NOT NULL,
    ordem INTEGER NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_unidade_videos_sigla
    ON unidade_videos (sigla, ordem);
