-- O toggle do CNS/PDQ nasceu global (V159), mas a credencial/certificado ja e por
-- unidade (integracao_sus_credencial) - um toggle geral nao tinha efeito real numa
-- unidade sem credencial propria, e dava a falsa impressao de "esta tudo ligado".
-- Passa a ser uma linha por unidade, com o mesmo efeito de antes quando nao existe
-- linha para a unidade (ativo = false, comportamento anterior a esta integracao).
ALTER TABLE configuracao_cns_pdq ADD COLUMN unidade VARCHAR(30);

-- A linha global antiga nao representa nenhuma unidade especifica - comeca do zero,
-- cada unidade e ativada explicitamente pela tela /base2/integracao-sus.
DELETE FROM configuracao_cns_pdq;

ALTER TABLE configuracao_cns_pdq ALTER COLUMN unidade SET NOT NULL;
ALTER TABLE configuracao_cns_pdq ADD CONSTRAINT uk_configuracao_cns_pdq_unidade UNIQUE (unidade);

CREATE SEQUENCE IF NOT EXISTS configuracao_cns_pdq_seq;
ALTER TABLE configuracao_cns_pdq ALTER COLUMN idconfiguracaocnspdq SET DEFAULT nextval('configuracao_cns_pdq_seq');
ALTER SEQUENCE configuracao_cns_pdq_seq OWNED BY configuracao_cns_pdq.idconfiguracaocnspdq;
