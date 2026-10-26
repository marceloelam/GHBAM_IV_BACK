CREATE TABLE IF NOT EXISTS consultorioagendastatus (
    idconsultorioagendastatus SERIAL PRIMARY KEY,
    unidade VARCHAR(25),
    idescalapaciente INTEGER DEFAULT 0,
    dataatendimento DATE,
    paciente VARCHAR(100),
    profissional VARCHAR(80),
    especialidade VARCHAR(80),
    status VARCHAR(20),
    observacao VARCHAR(500),
    criadoem TIMESTAMP,
    criadopor VARCHAR(80),
    atualizadoem TIMESTAMP,
    atualizadopor VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_consultorioagendastatus_unidade_data
    ON consultorioagendastatus (unidade, dataatendimento);

CREATE UNIQUE INDEX IF NOT EXISTS ux_consultorioagendastatus_agendamento_unidade
    ON consultorioagendastatus (unidade, idescalapaciente)
    WHERE idescalapaciente > 0;
