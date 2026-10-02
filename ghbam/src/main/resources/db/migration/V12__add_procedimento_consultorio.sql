CREATE TABLE IF NOT EXISTS procedimentoconsultorio (
    idprocedimentoconsultorio SERIAL PRIMARY KEY,
    unidade VARCHAR(25),
    codigo VARCHAR(30),
    nome VARCHAR(120),
    especialidade VARCHAR(80),
    tipocobranca VARCHAR(20),
    valorpadrao NUMERIC(12, 2) DEFAULT 0,
    valorrepassepadrao NUMERIC(12, 2) DEFAULT 0,
    ativo VARCHAR(1) DEFAULT 'S',
    criadoem TIMESTAMP,
    criadopor VARCHAR(80),
    atualizadoem TIMESTAMP,
    atualizadopor VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_procedimentoconsultorio_unidade_ativo
    ON procedimentoconsultorio (unidade, ativo);

CREATE UNIQUE INDEX IF NOT EXISTS ux_procedimentoconsultorio_unidade_codigo
    ON procedimentoconsultorio (unidade, codigo)
    WHERE codigo IS NOT NULL AND codigo <> '-' AND codigo <> '';

ALTER TABLE financeiroconsultorio
    ADD COLUMN IF NOT EXISTS idprocedimentoconsultorio INTEGER DEFAULT 0;

ALTER TABLE financeiroconsultorio
    ADD COLUMN IF NOT EXISTS procedimento VARCHAR(120);
