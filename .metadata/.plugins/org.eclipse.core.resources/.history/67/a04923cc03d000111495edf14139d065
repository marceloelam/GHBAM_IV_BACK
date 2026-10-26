CREATE TABLE IF NOT EXISTS configuracaoagendaconsultorio (
    idconfiguracaoagendaconsultorio SERIAL PRIMARY KEY,
    unidade VARCHAR(25),
    profissional VARCHAR(100),
    especialidade VARCHAR(80),
    sala VARCHAR(80),
    diasemana VARCHAR(20),
    horainicio TIME,
    horafim TIME,
    duracaominutos INTEGER DEFAULT 30,
    limiteencaixes INTEGER DEFAULT 0,
    intervaloretornodias INTEGER DEFAULT 30,
    permiteencaixe VARCHAR(1),
    ativo VARCHAR(1),
    observacao VARCHAR(500),
    criadoem TIMESTAMP,
    criadopor VARCHAR(80),
    atualizadoem TIMESTAMP,
    atualizadopor VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_configagendaconsultorio_unidade_profissional
    ON configuracaoagendaconsultorio (unidade, profissional);

CREATE INDEX IF NOT EXISTS idx_configagendaconsultorio_unidade_dia
    ON configuracaoagendaconsultorio (unidade, diasemana, horainicio);
