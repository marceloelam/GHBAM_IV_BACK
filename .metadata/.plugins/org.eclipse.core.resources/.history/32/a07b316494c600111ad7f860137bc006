CREATE TABLE IF NOT EXISTS financeiroconsultorio (
    idfinanceiroconsultorio SERIAL PRIMARY KEY,
    unidade VARCHAR(25),
    idpaciente INTEGER DEFAULT 0,
    idescalapaciente INTEGER DEFAULT 0,
    idatendimentomedicoagenda INTEGER DEFAULT 0,
    paciente VARCHAR(100),
    profissional VARCHAR(80),
    especialidade VARCHAR(80),
    dataatendimento DATE,
    tipocobranca VARCHAR(20),
    convenio VARCHAR(80),
    plano VARCHAR(80),
    numerocarteira VARCHAR(60),
    guiaautorizacao VARCHAR(60),
    valorconsulta NUMERIC(12, 2) DEFAULT 0,
    valorcoparticipacao NUMERIC(12, 2) DEFAULT 0,
    valordesconto NUMERIC(12, 2) DEFAULT 0,
    valorrecebido NUMERIC(12, 2) DEFAULT 0,
    valorrepassemedico NUMERIC(12, 2) DEFAULT 0,
    formapagamento VARCHAR(20),
    status VARCHAR(20),
    recibonumero VARCHAR(40),
    observacao VARCHAR(500),
    criadoem TIMESTAMP,
    criadopor VARCHAR(80),
    atualizadoem TIMESTAMP,
    atualizadopor VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_financeiroconsultorio_unidade_data
    ON financeiroconsultorio (unidade, dataatendimento);

CREATE INDEX IF NOT EXISTS idx_financeiroconsultorio_paciente
    ON financeiroconsultorio (idpaciente);
