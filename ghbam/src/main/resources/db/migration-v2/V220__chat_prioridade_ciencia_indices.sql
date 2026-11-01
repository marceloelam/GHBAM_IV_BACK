-- Chat dos profissionais: mensagem urgente com confirmação de ciência e índices
-- compatíveis com as consultas reais (lower(unidade) e busca incremental por id).
ALTER TABLE chat_mensagens ADD COLUMN IF NOT EXISTS prioridade VARCHAR(10) DEFAULT 'NORMAL';
ALTER TABLE chat_mensagens ADD COLUMN IF NOT EXISTS ciencia_em TIMESTAMP;
ALTER TABLE chat_mensagens ADD COLUMN IF NOT EXISTS ciencia_por VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_chat_mensagens_sala_lower
    ON chat_mensagens (lower(unidade), tipo, sala, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_chat_mensagens_privado_lower
    ON chat_mensagens (lower(unidade), tipo, remetente_matricula, destinatario_matricula, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_chat_mensagens_recebidas
    ON chat_mensagens (destinatario_matricula, tipo, criado_em DESC);
