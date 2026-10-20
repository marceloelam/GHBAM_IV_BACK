-- Complemento aditivo da V190 ja aplicada; nao altera seu checksum nem fluxos da CAF.
ALTER TABLE pharmacia_operacao_unidade
    ADD COLUMN lote_destino_id BIGINT REFERENCES pharmacia_lote(id),
    ADD COLUMN chave VARCHAR(80),
    ADD COLUMN hash VARCHAR(64),
    ADD CONSTRAINT ux_pharmacia_operacao_unidade_chave UNIQUE (unidade, chave);
CREATE INDEX idx_pharmacia_operacao_lote ON pharmacia_operacao_unidade(unidade, lote_id);
CREATE INDEX idx_pharmacia_operacao_destino ON pharmacia_operacao_unidade(unidade, lote_destino_id);
