-- Permite corrigir manualmente producao presa em PENDENTE_CLASSIFICACAO quando o
-- SIGTAP local (s_proced.pa_tpreg) nao define BPA-I/BPA-C para o procedimento.
-- Boolean (wrapper), mesmo padrao das demais colunas aditivas desta tabela.
ALTER TABLE afaturar ADD COLUMN IF NOT EXISTS classificacao_manual BOOLEAN DEFAULT FALSE;
