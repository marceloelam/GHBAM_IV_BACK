-- Aditiva; nao modifica tabelas ou fluxos da CAF. Executar pelo Flyway no deploy.
CREATE TABLE pharmacia_operacao_unidade (
    id BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    lote_id BIGINT NOT NULL REFERENCES pharmacia_lote(id),
    tipo VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    matricula VARCHAR(40) NOT NULL,
    avaliador VARCHAR(40),
    criado_em TIMESTAMP NOT NULL,
    avaliado_em TIMESTAMP,
    justificativa VARCHAR(1000) NOT NULL,
    parecer VARCHAR(1000),
    quantidade INTEGER CHECK (quantidade >= 0),
    saldo_referencia INTEGER,
    origem_id BIGINT REFERENCES pharmacia_operacao_unidade(id),
    dispensacao_id BIGINT REFERENCES pharmacia_dispensacao(id),
    temperatura NUMERIC(8,3), limite_minimo NUMERIC(8,3), limite_maximo NUMERIC(8,3),
    validade_uso TIMESTAMP, documento VARCHAR(120)
);
CREATE INDEX idx_pharmacia_operacao_unidade_data ON pharmacia_operacao_unidade(unidade, criado_em DESC);
CREATE INDEX idx_pharmacia_operacao_origem ON pharmacia_operacao_unidade(origem_id);
CREATE UNIQUE INDEX ux_pharmacia_devolucao ON pharmacia_operacao_unidade(dispensacao_id) WHERE dispensacao_id IS NOT NULL AND tipo = 'DEVOLUCAO';
CREATE TABLE pharmacia_condicao_unidade (
    id BIGSERIAL PRIMARY KEY, unidade VARCHAR(20) NOT NULL,
    lote_id BIGINT NOT NULL REFERENCES pharmacia_lote(id),
    bloqueado BOOLEAN NOT NULL DEFAULT FALSE,
    validade_uso TIMESTAMP, motivo VARCHAR(1000), UNIQUE(unidade, lote_id)
);
CREATE TABLE pharmacia_confirmacao_chave (
    id BIGSERIAL PRIMARY KEY, unidade VARCHAR(20) NOT NULL, chave VARCHAR(80) NOT NULL,
    hash VARCHAR(64) NOT NULL, paciente BIGINT NOT NULL, matricula VARCHAR(40) NOT NULL,
    criado_em TIMESTAMP NOT NULL, resposta_json TEXT NOT NULL, UNIQUE(unidade, chave)
);
-- Metadados de dispensacao: nullable para preservar o historico legado.
ALTER TABLE pharmacia_dispensacao ADD COLUMN prescricao_item_id INTEGER REFERENCES prescricao_item_estruturado(iditem);
ALTER TABLE pharmacia_dispensacao ADD COLUMN bam_id INTEGER;
ALTER TABLE pharmacia_dispensacao ADD COLUMN receita_referencia VARCHAR(120);
ALTER TABLE pharmacia_dispensacao ADD COLUMN receita_data DATE;
ALTER TABLE pharmacia_dispensacao ADD COLUMN receita_validade DATE;
ALTER TABLE pharmacia_dispensacao ADD COLUMN receita_retida BOOLEAN;
ALTER TABLE pharmacia_dispensacao ADD COLUMN conferida BOOLEAN;
ALTER TABLE pharmacia_dispensacao ADD COLUMN conferencia_justificativa VARCHAR(1000);
CREATE INDEX idx_pharmacia_disp_unidade_data ON pharmacia_dispensacao(unidade_sigla, data_hora);
CREATE INDEX idx_pharmacia_disp_prescricao ON pharmacia_dispensacao(prescricao_item_id);
