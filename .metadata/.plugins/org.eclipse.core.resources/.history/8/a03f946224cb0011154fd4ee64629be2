CREATE TABLE IF NOT EXISTS pep_anotacao_rascunho (
    id BIGSERIAL PRIMARY KEY,
    idbam INTEGER NOT NULL,
    unidade VARCHAR(120),
    descricao TEXT NOT NULL,
    criado_por_matricula VARCHAR(40) NOT NULL,
    criado_por_nome VARCHAR(160),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_por_matricula VARCHAR(40)
);

CREATE INDEX IF NOT EXISTS idx_pep_anotacao_rascunho_bam
    ON pep_anotacao_rascunho (idbam, criado_em DESC);

CREATE INDEX IF NOT EXISTS idx_pep_anotacao_rascunho_criador
    ON pep_anotacao_rascunho (criado_por_matricula, criado_em DESC);
