-- Garante que uma matrícula só tenha no máximo uma sessão ativa no banco
-- Esta constraint é a rede de segurança: mesmo que o cache em memória falhe
-- (hot reload, múltiplas instâncias), o banco impede duplicatas.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_unique_active_session
    ON sessoes_ativas (matricula)
    WHERE ativa = true;
