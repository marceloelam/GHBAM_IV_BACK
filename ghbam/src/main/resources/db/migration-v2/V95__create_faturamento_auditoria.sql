CREATE TABLE IF NOT EXISTS faturamento_auditoria (
  idfaturamentoauditoria SERIAL PRIMARY KEY,
  unidade VARCHAR(30),
  acao VARCHAR(60) NOT NULL,
  status VARCHAR(20) NOT NULL,
  entidade VARCHAR(60),
  identidade VARCHAR(40),
  valor_anterior TEXT,
  valor_novo TEXT,
  motivo VARCHAR(500),
  usuario VARCHAR(80),
  matricula VARCHAR(40),
  detalhes TEXT,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_faturamento_auditoria_entidade ON faturamento_auditoria (entidade, identidade);
CREATE INDEX IF NOT EXISTS idx_faturamento_auditoria_criado_em ON faturamento_auditoria (criado_em DESC);
