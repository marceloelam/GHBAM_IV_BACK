CREATE TABLE IF NOT EXISTS faturamento_competencia (
  idcompetencia SERIAL PRIMARY KEY,
  competencia VARCHAR(6) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
  versao_sigtap_utilizada VARCHAR(6),
  quantidade_producao INTEGER NOT NULL DEFAULT 0,
  quantidade_criticas INTEGER NOT NULL DEFAULT 0,
  valor_estimado NUMERIC(14,2) NOT NULL DEFAULT 0,
  data_abertura TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario_abertura VARCHAR(40),
  data_fechamento TIMESTAMP,
  usuario_fechamento VARCHAR(40),
  data_reabertura TIMESTAMP,
  usuario_reabertura VARCHAR(40),
  motivo_reabertura VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_faturamento_competencia_status ON faturamento_competencia (status);
