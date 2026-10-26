CREATE TABLE IF NOT EXISTS plantao (
    idplantao SERIAL PRIMARY KEY,
    servidor_idservidor INTEGER NOT NULL REFERENCES servidor(idservidor),
    unidade VARCHAR(30) NOT NULL,
    data_inicio TIMESTAMP NOT NULL,
    data_fim TIMESTAMP,
    tipo VARCHAR(50),
    setor VARCHAR(100),
    obs VARCHAR(500),
    cadastrou_idservidor INTEGER REFERENCES servidor(idservidor),
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS treinamento (
    idtreinamento SERIAL PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descricao TEXT,
    data_inicio DATE NOT NULL,
    data_fim DATE,
    carga_horaria INTEGER,
    categoria VARCHAR(100),
    unidade VARCHAR(30),
    obrigatorio BOOLEAN DEFAULT false,
    cadastrou_idservidor INTEGER REFERENCES servidor(idservidor),
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS treinamento_participante (
    id SERIAL PRIMARY KEY,
    treinamento_idtreinamento INTEGER NOT NULL REFERENCES treinamento(idtreinamento) ON DELETE CASCADE,
    servidor_idservidor INTEGER NOT NULL REFERENCES servidor(idservidor),
    status VARCHAR(30) DEFAULT 'PENDENTE',
    data_conclusao DATE,
    certificado VARCHAR(200),
    obs VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS certificacao (
    idcertificacao SERIAL PRIMARY KEY,
    servidor_idservidor INTEGER NOT NULL REFERENCES servidor(idservidor),
    titulo VARCHAR(200) NOT NULL,
    instituicao VARCHAR(200),
    data_emissao DATE,
    data_validade DATE,
    arquivo VARCHAR(500),
    unidade VARCHAR(30),
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
