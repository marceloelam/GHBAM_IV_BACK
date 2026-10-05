-- Anexos de referencia das operacoes; sem alterar saldo ou historico existente.
ALTER TABLE pharmacia_operacao_unidade ADD COLUMN documento_nome VARCHAR(120);
CREATE TABLE pharmacia_operacao_documento (
    operacao_id BIGINT PRIMARY KEY REFERENCES pharmacia_operacao_unidade(id),
    conteudo_base64 TEXT NOT NULL
);
