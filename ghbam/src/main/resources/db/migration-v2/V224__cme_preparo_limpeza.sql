-- CME: etapas antes da esterilizacao (RDC 15/2012) - recebimento no
-- expurgo, limpeza, inspecao e preparo/embalagem de cada caixa. A caixa
-- pronta vira pacote etiquetado num ciclo (cme_pacote.idpreparo).

CREATE TABLE IF NOT EXISTS cme_preparo (
    idpreparo BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    descricao VARCHAR(160) NOT NULL,
    contemimplante BOOLEAN NOT NULL DEFAULT FALSE,
    origem VARCHAR(80),
    codigoanterior VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEBIDO',
    datarecebimento TIMESTAMP,
    usuariorecebimento VARCHAR(80),
    metodolimpeza VARCHAR(30),
    datalimpeza TIMESTAMP,
    usuariolimpeza VARCHAR(80),
    inspecao VARCHAR(20),
    embalagem VARCHAR(40),
    datapreparo TIMESTAMP,
    usuariopreparo VARCHAR(80),
    observacao TEXT,
    idpacote BIGINT,
    datacarga TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cme_preparo_unidade_status
    ON cme_preparo (unidade, status);

ALTER TABLE cme_pacote
    ADD COLUMN IF NOT EXISTS idpreparo BIGINT;

CREATE INDEX IF NOT EXISTS idx_cme_pacote_preparo
    ON cme_pacote (idpreparo);
