CREATE TABLE IF NOT EXISTS passkey_credential (
    id BIGSERIAL PRIMARY KEY,
    matricula VARCHAR(15) NOT NULL,
    nome_dispositivo VARCHAR(120),
    credential_id TEXT NOT NULL UNIQUE,
    user_handle TEXT NOT NULL,
    public_key_cose TEXT NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    discoverable BOOLEAN NOT NULL DEFAULT FALSE,
    backup_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    backed_up BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_uso_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_passkey_credential_matricula
    ON passkey_credential (matricula);
CREATE INDEX IF NOT EXISTS idx_passkey_credential_user_handle
    ON passkey_credential (user_handle);

CREATE TABLE IF NOT EXISTS passkey_challenge (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(80) NOT NULL UNIQUE,
    tipo VARCHAR(20) NOT NULL,
    matricula VARCHAR(15),
    request_json TEXT NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_passkey_challenge_expira
    ON passkey_challenge (expira_em);

ALTER TABLE servidor ADD COLUMN IF NOT EXISTS celular_pareado_ativo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor ADD COLUMN IF NOT EXISTS celular_pareado_nome VARCHAR(120);
ALTER TABLE servidor ADD COLUMN IF NOT EXISTS celular_pareado_em TIMESTAMP;
