CREATE TABLE IF NOT EXISTS prontuario_clinico_item (
  iditem SERIAL PRIMARY KEY,
  idpaciente INTEGER NOT NULL,
  idbam INTEGER,
  unidade VARCHAR(20),
  tipo VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
  codigo VARCHAR(40),
  titulo VARCHAR(180),
  descricao TEXT,
  observacao TEXT,
  criado_por VARCHAR(120),
  criado_por_matricula VARCHAR(40),
  atualizado_por VARCHAR(120),
  atualizado_por_matricula VARCHAR(40),
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prontuario_clinico_item_paciente
  ON prontuario_clinico_item (idpaciente);

CREATE INDEX IF NOT EXISTS idx_prontuario_clinico_item_bam
  ON prontuario_clinico_item (idbam);

CREATE INDEX IF NOT EXISTS idx_prontuario_clinico_item_tipo
  ON prontuario_clinico_item (idpaciente, tipo, status);
