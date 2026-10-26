CREATE TABLE IF NOT EXISTS faturamento_retorno (
  idretorno SERIAL PRIMARY KEY,
  idremessa INTEGER,
  competencia VARCHAR(6),
  data_importacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario VARCHAR(40),
  quantidade_registros INTEGER NOT NULL DEFAULT 0,
  quantidade_aprovados INTEGER NOT NULL DEFAULT 0,
  quantidade_rejeitados INTEGER NOT NULL DEFAULT 0,
  observacao VARCHAR(500)
);
CREATE TABLE IF NOT EXISTS faturamento_retorno_item (
  idretornoitem SERIAL PRIMARY KEY,
  idretorno INTEGER NOT NULL,
  idafaturar INTEGER,
  codigo_externo VARCHAR(60),
  valor_apresentado NUMERIC(14,2) NOT NULL DEFAULT 0,
  valor_aprovado NUMERIC(14,2) NOT NULL DEFAULT 0,
  resultado VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  motivo VARCHAR(300),
  codigo_critica VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_faturamento_retorno_competencia ON faturamento_retorno (competencia);
CREATE INDEX IF NOT EXISTS idx_faturamento_retorno_item_retorno ON faturamento_retorno_item (idretorno);
CREATE INDEX IF NOT EXISTS idx_faturamento_retorno_item_afaturar ON faturamento_retorno_item (idafaturar);
