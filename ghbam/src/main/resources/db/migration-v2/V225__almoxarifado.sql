-- Almoxarifado geral (material de consumo, expediente, limpeza, rouparia,
-- manutencao). Separado da farmacia, que e exclusiva de medicamento com
-- validacao farmaceutica e dispensacao a paciente.

CREATE TABLE IF NOT EXISTS almox_item (
    iditem BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    codigo VARCHAR(40),
    descricao VARCHAR(160) NOT NULL,
    unidademedida VARCHAR(20) NOT NULL DEFAULT 'UN',
    grupo VARCHAR(30) NOT NULL DEFAULT 'CONSUMO',
    estoqueminimo NUMERIC(14,3) NOT NULL DEFAULT 0,
    estoquemaximo NUMERIC(14,3),
    saldo NUMERIC(14,3) NOT NULL DEFAULT 0,
    customedio NUMERIC(14,4) NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuarioregistro VARCHAR(80),
    dataatualizacao TIMESTAMP,
    usuarioatualizacao VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_almox_item_unidade_descricao
    ON almox_item (unidade, descricao);

CREATE INDEX IF NOT EXISTS idx_almox_item_unidade_ativo
    ON almox_item (unidade, ativo);

CREATE TABLE IF NOT EXISTS almox_requisicao (
    idrequisicao BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    setor VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    observacao TEXT,
    matriculasolicitante VARCHAR(80) NOT NULL,
    nomesolicitante VARCHAR(160),
    datasolicitacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    matriculaatendente VARCHAR(80),
    dataatendimento TIMESTAMP,
    motivo TEXT
);

CREATE INDEX IF NOT EXISTS idx_almox_requisicao_unidade_status
    ON almox_requisicao (unidade, status);

CREATE INDEX IF NOT EXISTS idx_almox_requisicao_solicitante
    ON almox_requisicao (unidade, matriculasolicitante);

CREATE TABLE IF NOT EXISTS almox_requisicao_item (
    idrequisicaoitem BIGSERIAL PRIMARY KEY,
    idrequisicao BIGINT NOT NULL REFERENCES almox_requisicao (idrequisicao),
    iditem BIGINT NOT NULL REFERENCES almox_item (iditem),
    quantidadesolicitada NUMERIC(14,3) NOT NULL,
    quantidadeatendida NUMERIC(14,3)
);

CREATE INDEX IF NOT EXISTS idx_almox_requisicao_item_req
    ON almox_requisicao_item (idrequisicao);

-- Toda alteracao de saldo passa por aqui (entrada, saida por requisicao, ajuste).
CREATE TABLE IF NOT EXISTS almox_movimentacao (
    idmovimentacao BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    iditem BIGINT NOT NULL REFERENCES almox_item (iditem),
    tipo VARCHAR(20) NOT NULL,
    quantidade NUMERIC(14,3) NOT NULL,
    custounitario NUMERIC(14,4),
    saldoapos NUMERIC(14,3) NOT NULL,
    documento VARCHAR(60),
    fornecedor VARCHAR(160),
    lote VARCHAR(60),
    validade DATE,
    setor VARCHAR(80),
    idrequisicao BIGINT,
    justificativa TEXT,
    usuario VARCHAR(80),
    datamovimentacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_almox_mov_item
    ON almox_movimentacao (iditem, datamovimentacao);

CREATE INDEX IF NOT EXISTS idx_almox_mov_unidade_tipo
    ON almox_movimentacao (unidade, tipo, datamovimentacao);

ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS palmoxarifado BOOLEAN NOT NULL DEFAULT FALSE;
