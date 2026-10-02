ALTER TABLE faturamento_retorno
    ADD COLUMN IF NOT EXISTS unidade VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_faturamento_retorno_competencia_unidade
    ON faturamento_retorno (competencia, unidade);
