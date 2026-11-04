-- CME: cadastro de caixas/kits por unidade com a composicao (instrumentais e quantidades).
-- Substitui a lista fixa do codigo e prepara a conferencia na chegada ao expurgo.
CREATE TABLE IF NOT EXISTS cme_caixa (
    idcaixa              BIGSERIAL PRIMARY KEY,
    unidade              VARCHAR(20)  NOT NULL,
    nome                 VARCHAR(160) NOT NULL,
    contem_implante      BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo                BOOLEAN      NOT NULL DEFAULT TRUE,
    observacao           VARCHAR(500),
    usuario_registro     VARCHAR(80),
    data_registro        TIMESTAMP    NOT NULL DEFAULT now(),
    usuario_atualizacao  VARCHAR(80),
    data_atualizacao     TIMESTAMP,
    CONSTRAINT uk_cme_caixa_unidade_nome UNIQUE (unidade, nome)
);

CREATE TABLE IF NOT EXISTS cme_caixa_item (
    iditem       BIGSERIAL PRIMARY KEY,
    idcaixa      BIGINT       NOT NULL REFERENCES cme_caixa (idcaixa) ON DELETE CASCADE,
    instrumento  VARCHAR(160) NOT NULL,
    quantidade   INTEGER      NOT NULL CHECK (quantidade > 0),
    ordem        INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_cme_caixa_item_caixa ON cme_caixa_item (idcaixa);
