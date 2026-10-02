CREATE TABLE IF NOT EXISTS classificacao_ghbam_item (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(80) NOT NULL UNIQUE,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    tipo VARCHAR(40) NOT NULL,
    titulo VARCHAR(180) NOT NULL,
    definicao TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    versao VARCHAR(20) NOT NULL DEFAULT '0.1.0',
    indicadores_json TEXT NOT NULL DEFAULT '[]',
    relacionamentos_json TEXT NOT NULL DEFAULT '[]',
    criado_por VARCHAR(120),
    criado_por_matricula VARCHAR(40),
    revisado_por VARCHAR(120),
    revisado_por_matricula VARCHAR(40),
    aprovado_por VARCHAR(120),
    aprovado_por_matricula VARCHAR(40),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aprovado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_classificacao_ghbam_status_tipo
    ON classificacao_ghbam_item (status, tipo);

ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS classificacaoghbam BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS classificacaoghbam_revisar BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS classificacaoghbam_aprovar BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS classificacaoghbam_admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS classificacaoghbam_auditar BOOLEAN NOT NULL DEFAULT FALSE;
