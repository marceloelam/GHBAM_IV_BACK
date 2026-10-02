-- G-PEC: integracoes configuradas pela tela (Configuracao > Integracoes), sem depender de
-- variavel de ambiente no servidor. Senhas gravadas criptografadas (AES-GCM, chave
-- ghbam.sus.credentials.key, mesmo esquema das credenciais SUS). Configuracao municipal
-- (uma linha por integracao), nao por unidade.
--   CENTRALIZADOR : PEC e-SUS APS Centralizador que recebe os registros LEDI (url, usuario, senha)
--   MIGRACAO_PEC  : banco PostgreSQL do PEC Centralizador, SO LEITURA, para migrar historico
CREATE TABLE IF NOT EXISTS aps_integracao (
    chave                   VARCHAR(30) PRIMARY KEY,
    url                     VARCHAR(300),
    host                    VARCHAR(200),
    porta                   INTEGER,
    banco                   VARCHAR(100),
    usuario                 VARCHAR(120),
    senha_cripto            TEXT,
    aceitar_autoassinado    BOOLEAN NOT NULL DEFAULT FALSE,
    observacao              VARCHAR(500),
    ultimo_teste_em         TIMESTAMP,
    ultimo_teste_ok         BOOLEAN,
    ultimo_teste_mensagem   VARCHAR(1000),
    atualizado_em           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_por          VARCHAR(20),
    CONSTRAINT ck_aps_integracao_chave CHECK (chave IN ('CENTRALIZADOR', 'MIGRACAO_PEC'))
);
