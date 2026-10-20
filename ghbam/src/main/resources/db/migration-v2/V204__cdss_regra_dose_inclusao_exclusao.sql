-- Limites de dose: medicamentos incluidos pela tela (origem MANUAL) e exclusao
-- logica (nada e apagado; o historico registra quem excluiu/restaurou e o motivo).
ALTER TABLE cdss_regra_dose ADD COLUMN IF NOT EXISTS origem VARCHAR(10) NOT NULL DEFAULT 'BASE';
ALTER TABLE cdss_regra_dose ADD COLUMN IF NOT EXISTS sinonimos TEXT;
ALTER TABLE cdss_regra_dose ADD COLUMN IF NOT EXISTS excluido BOOLEAN NOT NULL DEFAULT FALSE;
