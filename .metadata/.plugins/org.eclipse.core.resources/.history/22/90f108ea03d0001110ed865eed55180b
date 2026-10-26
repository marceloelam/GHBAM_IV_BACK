CREATE TABLE IF NOT EXISTS faturamento_sigtap_importacao (
  idsigtapimportacao SERIAL PRIMARY KEY,
  competencia VARCHAR(6) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IMPORTADA',
  quantidade_procedimentos INTEGER NOT NULL DEFAULT 0,
  quantidade_erros INTEGER NOT NULL DEFAULT 0,
  data_importacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario VARCHAR(40),
  observacao VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_faturamento_sigtap_importacao_competencia ON faturamento_sigtap_importacao (competencia);
