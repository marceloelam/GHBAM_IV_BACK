CREATE TABLE IF NOT EXISTS ged_documento (
  id SERIAL PRIMARY KEY,
  uuid VARCHAR(80) NOT NULL UNIQUE,
  tipo VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ATIVO',
  idpaciente INTEGER,
  idbam INTEGER,
  prontuario VARCHAR(40),
  nome_paciente VARCHAR(180),
  unidade VARCHAR(30),
  nome_arquivo VARCHAR(255) NOT NULL,
  tipo_mime VARCHAR(120) NOT NULL,
  tamanho_bytes BIGINT,
  descricao TEXT,
  conteudo_base64 TEXT NOT NULL,
  criado_por VARCHAR(120),
  criado_por_matricula VARCHAR(40),
  removido_por VARCHAR(120),
  removido_por_matricula VARCHAR(40),
  motivo_remocao TEXT,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  removido_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ged_documento_paciente ON ged_documento (idpaciente, criado_em DESC) WHERE idpaciente IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ged_documento_bam ON ged_documento (idbam, criado_em DESC) WHERE idbam IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ged_documento_prontuario ON ged_documento (prontuario, criado_em DESC) WHERE prontuario IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ged_documento_unidade ON ged_documento (unidade, criado_em DESC);

ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS ged BOOLEAN NOT NULL DEFAULT FALSE;
