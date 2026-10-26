CREATE TABLE IF NOT EXISTS chat_mensagens (
    idchatmensagem SERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    sala VARCHAR(40),
    unidade VARCHAR(50) NOT NULL,
    mensagem TEXT,
    remetente_matricula VARCHAR(20) NOT NULL,
    remetente_nome VARCHAR(120),
    remetente_cargo VARCHAR(80),
    destinatario_matricula VARCHAR(20),
    destinatario_nome VARCHAR(120),
    anexo_nome VARCHAR(255),
    anexo_tipo VARCHAR(120),
    anexo_tamanho BIGINT,
    anexo_conteudo TEXT,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_mensagens_sala
    ON chat_mensagens (unidade, tipo, sala, criado_em DESC);

CREATE INDEX IF NOT EXISTS idx_chat_mensagens_privado
    ON chat_mensagens (unidade, tipo, remetente_matricula, destinatario_matricula, criado_em DESC);
