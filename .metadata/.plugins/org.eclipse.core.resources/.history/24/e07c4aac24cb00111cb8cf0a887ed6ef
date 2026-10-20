CREATE TABLE IF NOT EXISTS anotacao_enfermagem (
  id SERIAL PRIMARY KEY,
  idpaciente INTEGER NOT NULL,
  idbam INTEGER,
  prontuario VARCHAR(40),
  nomepaciente VARCHAR(180),
  unidade VARCHAR(20),
  status VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
  anotacao TEXT NOT NULL,
  motivo TEXT,
  criado_por VARCHAR(120),
  criado_por_matricula VARCHAR(40),
  atualizado_por VARCHAR(120),
  atualizado_por_matricula VARCHAR(40),
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_anotacao_enfermagem_paciente
  ON anotacao_enfermagem (idpaciente, status);

CREATE INDEX IF NOT EXISTS idx_anotacao_enfermagem_bam
  ON anotacao_enfermagem (idbam, status);
