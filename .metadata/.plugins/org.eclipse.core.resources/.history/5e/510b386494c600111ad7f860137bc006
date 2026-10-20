CREATE TABLE IF NOT EXISTS prescricao_item_estruturado (
  iditem SERIAL PRIMARY KEY,
  idprescricao INTEGER,
  idpaciente INTEGER,
  idbam INTEGER NOT NULL,
  unidade VARCHAR(20),
  status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
  medicamento VARCHAR(180) NOT NULL,
  dose VARCHAR(80),
  via VARCHAR(60),
  frequencia VARCHAR(120),
  duracao VARCHAR(80),
  observacao TEXT,
  prescrito_por VARCHAR(120),
  prescrito_por_matricula VARCHAR(40),
  atualizado_por VARCHAR(120),
  atualizado_por_matricula VARCHAR(40),
  suspenso_por VARCHAR(120),
  suspenso_por_matricula VARCHAR(40),
  motivo_suspensao TEXT,
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP,
  suspenso_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prescricao_item_estruturado_bam
  ON prescricao_item_estruturado (idbam, status);

CREATE INDEX IF NOT EXISTS idx_prescricao_item_estruturado_prescricao
  ON prescricao_item_estruturado (idprescricao);

CREATE INDEX IF NOT EXISTS idx_prescricao_item_estruturado_paciente
  ON prescricao_item_estruturado (idpaciente);
