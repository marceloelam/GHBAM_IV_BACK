ALTER TABLE faturamento_remessa
    ADD COLUMN IF NOT EXISTS unidade VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_faturamento_remessa_competencia_unidade
    ON faturamento_remessa (idcompetencia, tipo, unidade);
