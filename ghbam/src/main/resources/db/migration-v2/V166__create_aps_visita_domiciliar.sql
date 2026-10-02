CREATE TABLE IF NOT EXISTS aps_visita_domiciliar (
  id BIGSERIAL PRIMARY KEY, unidade VARCHAR(30) NOT NULL, microarea VARCHAR(30) NOT NULL, id_cadastro BIGINT,
  nome_visitado VARCHAR(120) NOT NULL, profissional VARCHAR(120) NOT NULL, data_visita DATE NOT NULL, motivo VARCHAR(80) NOT NULL,
  desfecho VARCHAR(80), observacao VARCHAR(500), criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE aps_visita_domiciliar ADD COLUMN IF NOT EXISTS data_visita DATE;
CREATE INDEX IF NOT EXISTS idx_aps_visita_unidade_data ON aps_visita_domiciliar (unidade, data_visita);
CREATE INDEX IF NOT EXISTS idx_aps_visita_microarea ON aps_visita_domiciliar (unidade, microarea);
