CREATE TABLE IF NOT EXISTS sessoes_ativas (
    idsessao SERIAL PRIMARY KEY,
    matricula VARCHAR(15) NOT NULL,
    nome VARCHAR(55) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    ip_address VARCHAR(45),
    computador VARCHAR(100),
    unidade VARCHAR(30),
    data_login TIMESTAMP DEFAULT NOW(),
    ultima_atividade TIMESTAMP DEFAULT NOW(),
    ativa BOOLEAN DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_sessoes_ativas_matricula ON sessoes_ativas (matricula);
CREATE INDEX IF NOT EXISTS idx_sessoes_ativas_ativa ON sessoes_ativas (ativa);
CREATE INDEX IF NOT EXISTS idx_sessoes_ativas_token_hash ON sessoes_ativas (token_hash);
