ALTER TABLE faturamento_apac
    ADD COLUMN IF NOT EXISTS exportado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS data_exportacao TIMESTAMP,
    ADD COLUMN IF NOT EXISTS numero_remessa VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_faturamento_apac_exportacao_comp_unidade
    ON faturamento_apac (idcompetencia, unidade, status, exportado);
