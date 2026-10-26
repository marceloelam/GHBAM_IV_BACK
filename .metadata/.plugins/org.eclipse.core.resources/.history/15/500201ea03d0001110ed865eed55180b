-- Retificacao de prescricao (mesmo padrao aplicado a evolucaopacienteenf na V156):
-- somente o autor original pode corrigir seu proprio registro, dentro de um
-- prazo apos o registro (regra aplicada em codigo, nao no banco). Cada
-- retificacao gera uma NOVA linha, preservando o historico; a linha antiga
-- fica marcada como substituida.
ALTER TABLE prescricao
  ADD COLUMN IF NOT EXISTS versao INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS idprescricao_origem INTEGER,
  ADD COLUMN IF NOT EXISTS idprescricao_retificacao INTEGER,
  ADD COLUMN IF NOT EXISTS motivo_retificacao TEXT,
  ADD COLUMN IF NOT EXISTS matricula_autor VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_prescricao_matricula_autor ON prescricao (bam, matricula_autor);
CREATE INDEX IF NOT EXISTS idx_prescricao_origem ON prescricao (idprescricao_origem);
