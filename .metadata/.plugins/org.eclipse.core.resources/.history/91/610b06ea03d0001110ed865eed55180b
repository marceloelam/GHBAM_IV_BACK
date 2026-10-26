-- /base2/logs consulta "data = ? and unidade = ?" (LogRepository.filtarPorData) e a
-- tabela logs nao tinha indice: cada abertura da pagina varria a auditoria inteira.
-- IF NOT EXISTS: nao duplica caso o indice ja tenha sido criado manualmente no banco.
CREATE INDEX IF NOT EXISTS idx_logs_unidade_data ON logs (unidade, data);
