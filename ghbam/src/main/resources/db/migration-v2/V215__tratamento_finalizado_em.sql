-- Momento em que o medico finalizou o BAM (alta, internacao, observacao...).
-- Usado no BI da PA para medir a permanencia: entrada do BAM ate a finalizacao.
-- BAMs finalizados antes desta migration ficam sem valor (nao ha como recuperar a hora).
ALTER TABLE tratamento ADD COLUMN IF NOT EXISTS finalizado_em TIMESTAMP;
