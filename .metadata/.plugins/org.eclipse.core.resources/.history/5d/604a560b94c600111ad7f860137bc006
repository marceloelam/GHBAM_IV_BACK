-- Itens do menu NIR que antes so existiam nos menus Medico/Enfermagem/Administrativo.
-- Permissoes proprias (bloco "Permissoes NIR" em servidores2), independentes das originais.
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS nirlaudointernacao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS nirenfermariaseleitos BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS nirhistoricocompleto BOOLEAN NOT NULL DEFAULT FALSE;
