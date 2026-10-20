CREATE TABLE IF NOT EXISTS evidencia_assistencial (
    idevidencia BIGSERIAL PRIMARY KEY,
    modulo VARCHAR(40) NOT NULL,
    tipo VARCHAR(80) NOT NULL,
    status VARCHAR(40),
    unidade VARCHAR(20),
    idpaciente INTEGER,
    nomepaciente VARCHAR(160),
    referencia VARCHAR(80),
    responsavel VARCHAR(160),
    descricao TEXT,
    detalhes_json TEXT,
    data_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_registro VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_evidencia_assistencial_modulo_unidade
    ON evidencia_assistencial (modulo, unidade);

CREATE INDEX IF NOT EXISTS idx_evidencia_assistencial_status
    ON evidencia_assistencial (status);

ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcme BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS phemoterapia BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS psnd BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS prnds BOOLEAN DEFAULT FALSE;
