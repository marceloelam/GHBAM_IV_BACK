CREATE TABLE IF NOT EXISTS faturamento_critica (
  idfaturamentocritica SERIAL PRIMARY KEY,
  idafaturar INTEGER NOT NULL,
  codigo VARCHAR(40) NOT NULL,
  tipo VARCHAR(40) NOT NULL,
  severidade VARCHAR(20) NOT NULL DEFAULT 'ERRO',
  descricao VARCHAR(500) NOT NULL,
  campo_relacionado VARCHAR(60),
  valor_encontrado VARCHAR(200),
  valor_esperado VARCHAR(200),
  status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
  usuario_resolucao VARCHAR(40),
  data_resolucao TIMESTAMP,
  observacao VARCHAR(500),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_faturamento_critica_afaturar ON faturamento_critica (idafaturar);
CREATE INDEX IF NOT EXISTS idx_faturamento_critica_status ON faturamento_critica (status);
