CREATE TABLE IF NOT EXISTS integracao_sus_credencial (
    idintegracaosuscredencial SERIAL PRIMARY KEY,
    unidade VARCHAR(25) NOT NULL,
    ambiente VARCHAR(20) NOT NULL DEFAULT 'HOMOLOGACAO',
    orgao_gestor VARCHAR(120),
    usuario_tecnico VARCHAR(120),
    client_id VARCHAR(160),
    client_secret_criptografado TEXT,
    certificado_alias VARCHAR(160),
    certificado_pfx_criptografado TEXT,
    certificado_senha_criptografada TEXT,
    endpoint_base VARCHAR(300),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por VARCHAR(80),
    atualizado_por VARCHAR(80),
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_integracao_sus_credencial_unidade
    ON integracao_sus_credencial (lower(unidade));

CREATE TABLE IF NOT EXISTS integracao_sus_auditoria (
    idintegracaosusauditoria SERIAL PRIMARY KEY,
    unidade VARCHAR(25) NOT NULL,
    ambiente VARCHAR(20) NOT NULL,
    acao VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    usuario VARCHAR(80),
    matricula VARCHAR(80),
    detalhes TEXT,
    criado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_integracao_sus_auditoria_unidade_data
    ON integracao_sus_auditoria (lower(unidade), criado_em DESC);
