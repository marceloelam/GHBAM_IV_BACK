-- Almoxarifado, 2a etapa: fornecedores, pedido de compra (o recebimento da
-- entrada no estoque) e patrimonio (bens permanentes com historico).

CREATE TABLE IF NOT EXISTS almox_fornecedor (
    idfornecedor BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    nome VARCHAR(160) NOT NULL,
    cnpj VARCHAR(20),
    telefone VARCHAR(40),
    email VARCHAR(120),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuarioregistro VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_almox_fornecedor_unidade_nome
    ON almox_fornecedor (unidade, nome);

CREATE TABLE IF NOT EXISTS almox_compra (
    idcompra BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    idfornecedor BIGINT REFERENCES almox_fornecedor (idfornecedor),
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    observacao TEXT,
    notafiscal VARCHAR(60),
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuarioregistro VARCHAR(80),
    dataenvio TIMESTAMP,
    datarecebimento TIMESTAMP,
    usuariorecebimento VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_almox_compra_unidade_status
    ON almox_compra (unidade, status);

CREATE TABLE IF NOT EXISTS almox_compra_item (
    idcompraitem BIGSERIAL PRIMARY KEY,
    idcompra BIGINT NOT NULL REFERENCES almox_compra (idcompra),
    iditem BIGINT NOT NULL REFERENCES almox_item (iditem),
    quantidade NUMERIC(14,3) NOT NULL,
    custounitario NUMERIC(14,4),
    quantidaderecebida NUMERIC(14,3)
);

CREATE INDEX IF NOT EXISTS idx_almox_compra_item_compra
    ON almox_compra_item (idcompra);

CREATE TABLE IF NOT EXISTS almox_patrimonio (
    idpatrimonio BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    tombamento VARCHAR(40) NOT NULL,
    descricao VARCHAR(160) NOT NULL,
    categoria VARCHAR(30) NOT NULL DEFAULT 'OUTRO',
    marca VARCHAR(80),
    modelo VARCHAR(80),
    numeroserie VARCHAR(80),
    idfornecedor BIGINT REFERENCES almox_fornecedor (idfornecedor),
    notafiscal VARCHAR(60),
    dataaquisicao DATE,
    valoraquisicao NUMERIC(14,2),
    setor VARCHAR(80),
    responsavel VARCHAR(160),
    status VARCHAR(20) NOT NULL DEFAULT 'EM_USO',
    databaixa TIMESTAMP,
    motivobaixa TEXT,
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuarioregistro VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_almox_patrimonio_unidade_tombamento
    ON almox_patrimonio (unidade, tombamento);

CREATE INDEX IF NOT EXISTS idx_almox_patrimonio_unidade_status
    ON almox_patrimonio (unidade, status);

-- Historico do bem: cadastro, transferencia de setor, manutencao, retorno, baixa.
CREATE TABLE IF NOT EXISTS almox_patrimonio_evento (
    idevento BIGSERIAL PRIMARY KEY,
    idpatrimonio BIGINT NOT NULL REFERENCES almox_patrimonio (idpatrimonio),
    tipo VARCHAR(20) NOT NULL,
    setororigem VARCHAR(80),
    setordestino VARCHAR(80),
    detalhe TEXT,
    usuario VARCHAR(80),
    dataevento TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_almox_patrimonio_evento_bem
    ON almox_patrimonio_evento (idpatrimonio);
