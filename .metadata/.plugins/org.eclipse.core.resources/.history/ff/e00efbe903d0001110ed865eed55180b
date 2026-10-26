CREATE TABLE IF NOT EXISTS faturamento_raas (
  idraas SERIAL PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  idpaciente INTEGER NOT NULL,
  nomepaciente VARCHAR(80),
  cnspaciente VARCHAR(19),
  unidade VARCHAR(25),
  cnes VARCHAR(15),
  idprofissional INTEGER,
  matriculaprofissional VARCHAR(15),
  procedimento VARCHAR(10),
  cid VARCHAR(10),
  idcompetencia INTEGER,
  status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_criador VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_faturamento_raas_paciente ON faturamento_raas (idpaciente);
CREATE INDEX IF NOT EXISTS idx_faturamento_raas_status ON faturamento_raas (status);
CREATE INDEX IF NOT EXISTS idx_faturamento_raas_competencia ON faturamento_raas (idcompetencia);
