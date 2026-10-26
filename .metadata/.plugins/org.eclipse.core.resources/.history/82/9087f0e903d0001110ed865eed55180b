CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_documentos (
    idragdocumento SERIAL PRIMARY KEY,
    titulo VARCHAR(500) NOT NULL,
    conteudo TEXT NOT NULL,
    tipo VARCHAR(50) NOT NULL DEFAULT 'texto',
    setor VARCHAR(50),
    idpaciente INTEGER DEFAULT 0,
    idprontuario INTEGER DEFAULT 0,
    metadados JSONB DEFAULT '{}',
    embedding vector(384),
    criadoem TIMESTAMP DEFAULT NOW(),
    atualizadoem TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rag_documentos_tipo ON rag_documentos (tipo);
CREATE INDEX IF NOT EXISTS idx_rag_documentos_setor ON rag_documentos (setor);
CREATE INDEX IF NOT EXISTS idx_rag_documentos_embedding ON rag_documentos
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
