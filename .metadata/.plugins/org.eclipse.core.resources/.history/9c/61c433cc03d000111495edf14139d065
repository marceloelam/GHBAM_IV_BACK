CREATE TABLE IF NOT EXISTS faturamento_aih (
  idaih SERIAL PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  idinternacao INTEGER NOT NULL,
  idpaciente INTEGER NOT NULL DEFAULT 0,
  prontuario VARCHAR(20),
  bam VARCHAR(20),
  unidade VARCHAR(25),
  cnes VARCHAR(15),
  procedimento_principal VARCHAR(10),
  cid VARCHAR(10),
  carater_atendimento VARCHAR(20),
  idcompetencia INTEGER,
  status VARCHAR(30) NOT NULL DEFAULT 'PRE_AIH',
  valor_estimado NUMERIC(14,2) NOT NULL DEFAULT 0,
  data_internacao TIMESTAMP,
  data_alta TIMESTAMP,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_criador VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_faturamento_aih_internacao ON faturamento_aih (idinternacao);
CREATE INDEX IF NOT EXISTS idx_faturamento_aih_status ON faturamento_aih (status);
CREATE INDEX IF NOT EXISTS idx_faturamento_aih_competencia ON faturamento_aih (idcompetencia);
