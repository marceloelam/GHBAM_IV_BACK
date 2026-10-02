-- Consultar documentos do GED passa a ser permissao propria: ate aqui o toggle
-- "GED - Consulta de documentos" existia so no cadastro e nunca era persistido.
-- ged (anexar) e gedremover vieram na linha antiga de migrations (V70/V71) e sao
-- recriados aqui com IF NOT EXISTS para o schema v2 ficar completo.
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS ged BOOLEAN DEFAULT FALSE;

ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS gedremover BOOLEAN DEFAULT FALSE;

ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS gedconsulta BOOLEAN DEFAULT FALSE;
