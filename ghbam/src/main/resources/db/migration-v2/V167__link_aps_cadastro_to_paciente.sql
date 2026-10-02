ALTER TABLE aps_cadastro_territorial ADD COLUMN IF NOT EXISTS id_paciente INTEGER;
CREATE INDEX IF NOT EXISTS idx_aps_cadastro_paciente ON aps_cadastro_territorial (id_paciente);
