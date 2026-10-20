CREATE TABLE IF NOT EXISTS faturamento_remessa (
  idremessa SERIAL PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  idcompetencia INTEGER NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  layout VARCHAR(20),
  status VARCHAR(20) NOT NULL DEFAULT 'GERADA',
  quantidade INTEGER NOT NULL DEFAULT 0,
  valor_estimado NUMERIC(14,2) NOT NULL DEFAULT 0,
  hash VARCHAR(64),
  tamanho INTEGER,
  caminho_arquivo VARCHAR(300),
  remessa_original_id INTEGER,
  data_geracao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario_geracao VARCHAR(40),
  data_transmissao TIMESTAMP,
  usuario_transmissao VARCHAR(40),
  protocolo_transmissao VARCHAR(80),
  observacao_transmissao VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_faturamento_remessa_competencia ON faturamento_remessa (idcompetencia, tipo);
CREATE INDEX IF NOT EXISTS idx_faturamento_remessa_status ON faturamento_remessa (status);
