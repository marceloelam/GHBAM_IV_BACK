CREATE TABLE IF NOT EXISTS procedimentoconsultoriopreco (
    idprocedimentoconsultoriopreco SERIAL PRIMARY KEY,
    unidade VARCHAR(25),
    idprocedimentoconsultorio INTEGER DEFAULT 0,
    tipocobranca VARCHAR(20),
    convenio VARCHAR(80),
    plano VARCHAR(80),
    valor NUMERIC(12, 2) DEFAULT 0,
    valorrepasse NUMERIC(12, 2) DEFAULT 0,
    ativo VARCHAR(1) DEFAULT 'S',
    criadoem TIMESTAMP,
    criadopor VARCHAR(80),
    atualizadoem TIMESTAMP,
    atualizadopor VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_procedimentoconsultoriopreco_lookup
    ON procedimentoconsultoriopreco (unidade, idprocedimentoconsultorio, tipocobranca, convenio, plano, ativo);

CREATE UNIQUE INDEX IF NOT EXISTS ux_procedimentoconsultoriopreco_escopo
    ON procedimentoconsultoriopreco (unidade, idprocedimentoconsultorio, tipocobranca, convenio, plano);
