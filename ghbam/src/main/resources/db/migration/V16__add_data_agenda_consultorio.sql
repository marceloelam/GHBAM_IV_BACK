ALTER TABLE configuracaoagendaconsultorio
    ADD COLUMN IF NOT EXISTS dataagenda DATE;

CREATE INDEX IF NOT EXISTS idx_configagendaconsultorio_unidade_data
    ON configuracaoagendaconsultorio (unidade, dataagenda, horainicio);
