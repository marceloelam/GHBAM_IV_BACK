-- Retificacao de evolucao de enfermagem/multidisciplinar: somente o autor original
-- pode corrigir seu proprio registro, dentro de um prazo apos o registro (regra
-- aplicada em codigo, nao no banco). Cada retificacao gera uma NOVA linha,
-- preservando o historico; a linha antiga fica marcada como substituida.
ALTER TABLE evolucaopacienteenf
  ADD COLUMN IF NOT EXISTS versao INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS idevolucao_origem INTEGER,
  ADD COLUMN IF NOT EXISTS idevolucao_retificacao INTEGER,
  ADD COLUMN IF NOT EXISTS motivo_retificacao TEXT,
  ADD COLUMN IF NOT EXISTS matricula_autor VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_evolucao_enf_matricula_autor ON evolucaopacienteenf (nbam, matricula_autor);
CREATE INDEX IF NOT EXISTS idx_evolucao_enf_origem ON evolucaopacienteenf (idevolucao_origem);
