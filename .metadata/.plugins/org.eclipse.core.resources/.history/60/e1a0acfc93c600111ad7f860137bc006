CREATE TABLE IF NOT EXISTS sae_enfermagem (
  id SERIAL PRIMARY KEY,
  uuid VARCHAR(80) NOT NULL UNIQUE,
  status VARCHAR(30) NOT NULL DEFAULT 'EM_ANDAMENTO',
  data_sae DATE NOT NULL,
  idpaciente INTEGER,
  idbam INTEGER,
  prontuario VARCHAR(40),
  nomepaciente VARCHAR(180),
  unidade VARCHAR(30),
  paciente_json TEXT,
  historico_json TEXT,
  diagnosticos_json TEXT,
  cuidados_json TEXT,
  prescricoes_json TEXT,
  evolucoes_json TEXT,
  criado_por VARCHAR(120),
  criado_por_matricula VARCHAR(40),
  atualizado_por VARCHAR(120),
  atualizado_por_matricula VARCHAR(40),
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sae_enfermagem_bam_data
  ON sae_enfermagem (idbam, data_sae);

CREATE INDEX IF NOT EXISTS idx_sae_enfermagem_paciente_data
  ON sae_enfermagem (idpaciente, data_sae);

CREATE INDEX IF NOT EXISTS idx_sae_enfermagem_status
  ON sae_enfermagem (status);
