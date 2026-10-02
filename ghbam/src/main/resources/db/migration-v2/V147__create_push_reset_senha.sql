-- Push Notification + Password Reset via PWA
-- Estrutura para pareamento de dispositivos (celular) e fluxo de recuperação de senha via Web Push

CREATE TABLE IF NOT EXISTS push_pareamento_token (
    id BIGSERIAL PRIMARY KEY,
    matricula VARCHAR(15) NOT NULL,
    token VARCHAR(100) NOT NULL UNIQUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_em TIMESTAMP NOT NULL,
    usado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_push_pareamento_token_matricula
    ON push_pareamento_token (matricula);
CREATE INDEX IF NOT EXISTS idx_push_pareamento_token_token
    ON push_pareamento_token (token);

CREATE TABLE IF NOT EXISTS push_dispositivo (
    id BIGSERIAL PRIMARY KEY,
    matricula VARCHAR(15) NOT NULL,
    endpoint TEXT NOT NULL UNIQUE,
    p256dh TEXT NOT NULL,
    auth_key TEXT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_uso TIMESTAMP,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_push_dispositivo_matricula
    ON push_dispositivo (matricula);

CREATE TABLE IF NOT EXISTS reset_senha_solicitacao (
    id BIGSERIAL PRIMARY KEY,
    matricula VARCHAR(15) NOT NULL,
    ip_solicitante VARCHAR(45),
    computador VARCHAR(100),
    codigo_confirmacao VARCHAR(4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    aprovar_token VARCHAR(100) UNIQUE,
    token_confirmacao VARCHAR(100) UNIQUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_em TIMESTAMP NOT NULL,
    respondido_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reset_senha_solicitacao_matricula
    ON reset_senha_solicitacao (matricula);
CREATE INDEX IF NOT EXISTS idx_reset_senha_solicitacao_status
    ON reset_senha_solicitacao (status, expira_em);
