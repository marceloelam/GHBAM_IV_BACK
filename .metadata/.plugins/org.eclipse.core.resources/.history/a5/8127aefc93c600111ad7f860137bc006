CREATE TABLE IF NOT EXISTS controle_assistencial_registro (
    id SERIAL PRIMARY KEY,
    uuid VARCHAR(80) NOT NULL UNIQUE,
    tipo VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REGISTRADO',
    unidade VARCHAR(30),
    setor VARCHAR(120),
    data_registro DATE NOT NULL,
    turno VARCHAR(20),
    responsavel VARCHAR(120),
    responsavel_matricula VARCHAR(40),
    responsavel_cargo VARCHAR(80),
    conselho VARCHAR(20),
    numero_conselho VARCHAR(40),
    idbam INTEGER,
    idpaciente INTEGER,
    paciente VARCHAR(180),
    dados_json TEXT,
    observacao TEXT,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_controle_assistencial_unidade_data
    ON controle_assistencial_registro (unidade, data_registro DESC, tipo);

CREATE INDEX IF NOT EXISTS idx_controle_assistencial_tipo_status
    ON controle_assistencial_registro (tipo, status, data_registro DESC);

CREATE INDEX IF NOT EXISTS idx_controle_assistencial_bam
    ON controle_assistencial_registro (idbam);
