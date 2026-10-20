-- Vinculo servidor <-> unidade (ate 3 por servidor). servidor.unidade continua
-- existindo por enquanto como a unidade "principal" (compat com codigo antigo
-- que ainda le direto da coluna); o login passa a decidir a unidade da sessao
-- com base nesta tabela quando o servidor tiver mais de um vinculo.
CREATE TABLE IF NOT EXISTS servidor_unidade (
  id SERIAL PRIMARY KEY,
  idservidor INTEGER NOT NULL REFERENCES servidor (idservidor) ON DELETE CASCADE,
  unidade VARCHAR(30) NOT NULL,
  principal BOOLEAN NOT NULL DEFAULT FALSE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (idservidor, unidade)
);

CREATE INDEX IF NOT EXISTS idx_servidor_unidade_servidor ON servidor_unidade (idservidor);
CREATE INDEX IF NOT EXISTS idx_servidor_unidade_unidade ON servidor_unidade (unidade);

-- Migra o dado atual: cada servidor ja tem exatamente 1 unidade hoje, que vira
-- o vinculo principal no novo modelo.
INSERT INTO servidor_unidade (idservidor, unidade, principal)
SELECT idservidor, unidade, TRUE
FROM servidor
WHERE unidade IS NOT NULL AND unidade <> ''
ON CONFLICT (idservidor, unidade) DO NOTHING;
