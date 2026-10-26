CREATE TABLE IF NOT EXISTS chat_conversas_limpas (
    idchatconversalimpa SERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    sala VARCHAR(40),
    unidade VARCHAR(50) NOT NULL,
    usuario_matricula VARCHAR(20) NOT NULL,
    outro_matricula VARCHAR(20),
    ultimo_id_limpo INTEGER,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_conversas_limpas_sala
    ON chat_conversas_limpas (unidade, tipo, usuario_matricula, sala);

CREATE INDEX IF NOT EXISTS idx_chat_conversas_limpas_privado
    ON chat_conversas_limpas (unidade, tipo, usuario_matricula, outro_matricula);
