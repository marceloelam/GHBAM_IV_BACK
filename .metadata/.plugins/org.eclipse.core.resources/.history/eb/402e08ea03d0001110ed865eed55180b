CREATE TABLE IF NOT EXISTS tiss_conta (
    idtissconta SERIAL PRIMARY KEY,
    bam INTEGER DEFAULT 0,
    idpaciente INTEGER DEFAULT 0,
    nomepaciente VARCHAR(120),
    unidade VARCHAR(25),
    competencia VARCHAR(20),
    tipo VARCHAR(20),
    status VARCHAR(20),
    operadora VARCHAR(120),
    registroans VARCHAR(20),
    plano VARCHAR(120),
    numerocarteira VARCHAR(40),
    numeroautorizacao VARCHAR(40),
    dataabertura VARCHAR(20),
    datafechamento VARCHAR(20),
    criado_por VARCHAR(80),
    atualizado_por VARCHAR(80),
    origem VARCHAR(30),
    observacao VARCHAR(700),
    valor_total NUMERIC(14, 2) DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tiss_conta_item (
    idtisscontaitem SERIAL PRIMARY KEY,
    idtissconta INTEGER NOT NULL,
    tipo_item VARCHAR(30),
    tabela VARCHAR(30),
    codigo VARCHAR(30),
    descricao VARCHAR(250),
    quantidade NUMERIC(14, 4) DEFAULT 0,
    valor_unitario NUMERIC(14, 2) DEFAULT 0,
    valor_total NUMERIC(14, 2) DEFAULT 0,
    data_execucao VARCHAR(20),
    executante VARCHAR(80),
    conselho VARCHAR(20),
    uf_conselho VARCHAR(10),
    cbo VARCHAR(20),
    numeroautorizacao VARCHAR(40),
    origem VARCHAR(30),
    origem_id VARCHAR(80),
    status VARCHAR(20),
    observacao VARCHAR(700),
    CONSTRAINT fk_tiss_conta_item_conta FOREIGN KEY (idtissconta) REFERENCES tiss_conta(idtissconta)
);

CREATE INDEX IF NOT EXISTS idx_tiss_conta_bam ON tiss_conta (bam);
CREATE INDEX IF NOT EXISTS idx_tiss_conta_periodo_unidade ON tiss_conta (dataabertura, unidade);
CREATE INDEX IF NOT EXISTS idx_tiss_conta_status ON tiss_conta (status);
CREATE INDEX IF NOT EXISTS idx_tiss_conta_item_conta ON tiss_conta_item (idtissconta);
