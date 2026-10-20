ALTER TABLE afaturar
    ADD COLUMN IF NOT EXISTS idcompetencia INTEGER,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE_CLASSIFICACAO',
    ADD COLUMN IF NOT EXISTS instrumento_registro VARCHAR(30),
    ADD COLUMN IF NOT EXISTS origem_modulo VARCHAR(30),
    ADD COLUMN IF NOT EXISTS origem_id INTEGER,
    ADD COLUMN IF NOT EXISTS possui_critica BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS faturado BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS exportado BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS datacriacao TIMESTAMP,
    ADD COLUMN IF NOT EXISTS dataalteracao TIMESTAMP,
    ADD COLUMN IF NOT EXISTS usuariocriador VARCHAR(40),
    ADD COLUMN IF NOT EXISTS usuarioalterador VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_afaturar_idcompetencia ON afaturar (idcompetencia);
CREATE INDEX IF NOT EXISTS idx_afaturar_status ON afaturar (status);
CREATE INDEX IF NOT EXISTS idx_afaturar_origem ON afaturar (origem_modulo, origem_id);
