CREATE TABLE IF NOT EXISTS faturamento_apac (
  idapac SERIAL PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  idpaciente INTEGER NOT NULL,
  nomepaciente VARCHAR(80),
  cnspaciente VARCHAR(19),
  unidade VARCHAR(25),
  cnes VARCHAR(15),
  idprofissional INTEGER,
  matriculaprofissional VARCHAR(15),
  procedimentoprincipal VARCHAR(10),
  procedimentossecundarios VARCHAR(200),
  cid VARCHAR(10),
  numeroautorizacao VARCHAR(30),
  idcompetencia INTEGER,
  quantidade INTEGER NOT NULL DEFAULT 1,
  status VARCHAR(30) NOT NULL DEFAULT 'SOLICITADA',
  data_solicitacao TIMESTAMP,
  data_autorizacao TIMESTAMP,
  usuario_autorizacao VARCHAR(40),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_criador VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_faturamento_apac_paciente ON faturamento_apac (idpaciente);
CREATE INDEX IF NOT EXISTS idx_faturamento_apac_status ON faturamento_apac (status);
CREATE INDEX IF NOT EXISTS idx_faturamento_apac_competencia ON faturamento_apac (idcompetencia);
