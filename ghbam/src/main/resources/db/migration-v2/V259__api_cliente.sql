-- Credenciais de SISTEMAS integradores (laboratorio, RNDS, outro municipio...).
-- O sistema troca client_id + segredo por um JWT com unidade e permissoes proprias
-- (POST /auth/cliente/token); o token passa pelas mesmas regras de RBAC e unidade.
-- O segredo nunca e' guardado: so o hash BCrypt. Cadastro so pelo superusuario.
CREATE TABLE IF NOT EXISTS api_cliente (
    id            BIGSERIAL PRIMARY KEY,
    client_id     VARCHAR(64)   NOT NULL UNIQUE,
    segredo_hash  VARCHAR(100)  NOT NULL,
    nome          VARCHAR(120)  NOT NULL,
    unidade       VARCHAR(40)   NOT NULL,
    permissoes    VARCHAR(2000) NOT NULL DEFAULT '',
    ativo         BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_por    VARCHAR(30),
    criado_em     TIMESTAMP     NOT NULL DEFAULT now(),
    ultimo_uso    TIMESTAMP
);
