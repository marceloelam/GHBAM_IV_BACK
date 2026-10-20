-- Plano de cuidados baseado na Classificação própria GHBAM.
-- Guarda cópia (snapshot) da versão exata do item de catálogo usada, para
-- rastreabilidade mesmo que o catálogo seja versionado ou arquivado depois.
-- Nenhum item é incluído automaticamente: toda inclusão é decisão registrada
-- do profissional de saúde autenticado.

CREATE TABLE IF NOT EXISTS classificacao_ghbam_plano_item (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(80) NOT NULL UNIQUE,
    bam INTEGER NOT NULL,
    idpaciente INTEGER,
    unidade VARCHAR(60),
    item_uuid VARCHAR(80) NOT NULL,
    item_codigo VARCHAR(40) NOT NULL,
    item_versao VARCHAR(20) NOT NULL,
    item_tipo VARCHAR(40) NOT NULL,
    item_titulo VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    observacao TEXT,
    avaliacao VARCHAR(20),
    avaliacao_observacao TEXT,
    avaliado_por VARCHAR(120),
    avaliado_por_matricula VARCHAR(40),
    avaliado_em TIMESTAMP,
    incluido_por VARCHAR(120),
    incluido_por_matricula VARCHAR(40),
    incluido_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    encerrado_por VARCHAR(120),
    encerrado_por_matricula VARCHAR(40),
    encerrado_em TIMESTAMP,
    motivo_encerramento TEXT
);

CREATE INDEX IF NOT EXISTS idx_classificacao_ghbam_plano_bam
    ON classificacao_ghbam_plano_item (bam, status);

CREATE TABLE IF NOT EXISTS classificacao_ghbam_plano_execucao (
    id BIGSERIAL PRIMARY KEY,
    plano_item_uuid VARCHAR(80) NOT NULL,
    bam INTEGER NOT NULL,
    situacao VARCHAR(20) NOT NULL,
    observacao TEXT,
    executado_por VARCHAR(120),
    executado_por_matricula VARCHAR(40),
    executado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_classificacao_ghbam_plano_execucao_item
    ON classificacao_ghbam_plano_execucao (plano_item_uuid, executado_em DESC);
