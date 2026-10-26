-- Toggle global (nao por unidade) que liga/desliga a consulta oficial do CNS/PDQ do
-- DATASUS em toda a /recepcao (e futuros consumidores). Uma unica linha, id fixo = 1.
-- Enquanto ativo=false, o sistema usa exclusivamente o scraping do CNES (comportamento
-- anterior a esta integracao). Controlado pela tela /base2/integracao-sus.
CREATE TABLE configuracao_cns_pdq (
    idconfiguracaocnspdq INTEGER PRIMARY KEY,
    ativo BOOLEAN NOT NULL DEFAULT FALSE,
    atualizadopor VARCHAR(80),
    atualizadoem TIMESTAMP
);

INSERT INTO configuracao_cns_pdq (idconfiguracaocnspdq, ativo) VALUES (1, FALSE);
