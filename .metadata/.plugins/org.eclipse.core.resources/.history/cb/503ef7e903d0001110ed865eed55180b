CREATE SEQUENCE IF NOT EXISTS diagnostico_sistema_iddiagnosticosistema_seq;

CREATE TABLE IF NOT EXISTS diagnostico_sistema (
    iddiagnosticosistema INTEGER PRIMARY KEY DEFAULT nextval('diagnostico_sistema_iddiagnosticosistema_seq'),
    unidade VARCHAR(30),
    usuario VARCHAR(120),
    matricula VARCHAR(40),
    tipo VARCHAR(40),
    modulo VARCHAR(80),
    severidade VARCHAR(40),
    metodo VARCHAR(12),
    endpoint VARCHAR(260),
    rota VARCHAR(260),
    status_http INTEGER,
    duracao_ms INTEGER,
    motivo VARCHAR(180),
    detalhes TEXT,
    criado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_diagnostico_sistema_unidade_tipo ON diagnostico_sistema (unidade, tipo);
CREATE INDEX IF NOT EXISTS idx_diagnostico_sistema_modulo_criado ON diagnostico_sistema (modulo, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_diagnostico_sistema_criado_em ON diagnostico_sistema (criado_em DESC);