-- GHBAM APS: idempotencia da sincronizacao do app offline do ACS/ACE.
-- Cada registro feito no celular leva um id local (UUID); reenviar o mesmo
-- item (queda de rede no meio do envio) devolve o resultado ja gravado.
CREATE TABLE IF NOT EXISTS aps_sync_item (
    id_local        VARCHAR(64) PRIMARY KEY,
    matricula       VARCHAR(20) NOT NULL,
    tipo            VARCHAR(30) NOT NULL,
    sucesso         BOOLEAN NOT NULL,
    resultado       JSONB,
    erro            TEXT,
    criado_em       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_aps_sync_matricula ON aps_sync_item (matricula, criado_em);
