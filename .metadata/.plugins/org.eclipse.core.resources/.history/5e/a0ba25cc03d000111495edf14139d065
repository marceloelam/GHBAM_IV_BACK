ALTER TABLE chat_mensagens
    ADD COLUMN IF NOT EXISTS visualizada_em TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_chat_mensagens_visualizacao
    ON chat_mensagens (destinatario_matricula, visualizada_em);
