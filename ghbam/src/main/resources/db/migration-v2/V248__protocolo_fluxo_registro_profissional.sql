-- Registro profissional (ex.: "CRM 12345", "COREN 6789") de quem decide a aprovacao e de quem
-- executa o protocolo. Vem do usuario autenticado no backend, nunca do cliente.
ALTER TABLE protocolo_fluxo_aprovacao ADD COLUMN IF NOT EXISTS registro VARCHAR(60);
ALTER TABLE protocolo_fluxo_execucao ADD COLUMN IF NOT EXISTS registro VARCHAR(60);
