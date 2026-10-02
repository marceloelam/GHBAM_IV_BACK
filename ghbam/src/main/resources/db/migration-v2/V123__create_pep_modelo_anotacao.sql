CREATE TABLE IF NOT EXISTS pep_modelo_anotacao (
    id BIGSERIAL PRIMARY KEY
);

ALTER TABLE pep_modelo_anotacao
    ADD COLUMN IF NOT EXISTS unidade VARCHAR(120),
    ADD COLUMN IF NOT EXISTS nome VARCHAR(120),
    ADD COLUMN IF NOT EXISTS texto TEXT,
    ADD COLUMN IF NOT EXISTS criado_por_matricula VARCHAR(40),
    ADD COLUMN IF NOT EXISTS criado_por_nome VARCHAR(160),
    ADD COLUMN IF NOT EXISTS criado_em TIMESTAMP DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS atualizado_em TIMESTAMP DEFAULT NOW();

UPDATE pep_modelo_anotacao
SET criado_em = NOW()
WHERE criado_em IS NULL;

UPDATE pep_modelo_anotacao
SET atualizado_em = COALESCE(criado_em, NOW())
WHERE atualizado_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_pep_modelo_anotacao_unidade
    ON pep_modelo_anotacao (unidade, atualizado_em DESC);

CREATE INDEX IF NOT EXISTS idx_pep_modelo_anotacao_criador
    ON pep_modelo_anotacao (criado_por_matricula);
