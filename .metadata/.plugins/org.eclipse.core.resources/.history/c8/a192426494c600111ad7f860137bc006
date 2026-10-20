-- Um mesmo celular pode ser autorizado por mais de uma matricula, mas apenas
-- uma vez por matricula. O endpoint deixa de ser globalmente exclusivo.
ALTER TABLE push_dispositivo
    DROP CONSTRAINT IF EXISTS push_dispositivo_endpoint_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_push_dispositivo_matricula_endpoint
    ON push_dispositivo (matricula, endpoint);
