ALTER TABLE faturamento_sigtap_importacao
    ADD COLUMN IF NOT EXISTS unidade VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_faturamento_sigtap_importacao_competencia_unidade
    ON faturamento_sigtap_importacao (competencia, unidade);

ALTER TABLE faturamento_competencia
    ADD COLUMN IF NOT EXISTS unidade VARCHAR(40);

ALTER TABLE faturamento_competencia
    DROP CONSTRAINT IF EXISTS faturamento_competencia_competencia_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_faturamento_competencia_competencia_unidade
    ON faturamento_competencia (competencia, unidade);
