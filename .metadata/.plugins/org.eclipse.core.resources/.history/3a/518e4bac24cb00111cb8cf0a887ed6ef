ALTER TABLE documento_assistencial
  ADD COLUMN IF NOT EXISTS cancelado_por VARCHAR(120),
  ADD COLUMN IF NOT EXISTS cancelado_por_matricula VARCHAR(40),
  ADD COLUMN IF NOT EXISTS motivo_cancelamento TEXT,
  ADD COLUMN IF NOT EXISTS cancelado_em TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_documento_assistencial_status
  ON documento_assistencial (status);
