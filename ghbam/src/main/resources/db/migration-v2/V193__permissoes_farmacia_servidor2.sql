-- Permissoes por area da farmacia (bloco Farmacia em servidores2). Quem ja tem
-- "Farmacia Adm" (servidor.farmcentral) ou o legado "remedios" continua com
-- acesso total; estas colunas permitem liberar so parte do modulo.
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS farmaciaprodutos BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS farmaciaestoque BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS farmaciadispensacao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS farmaciapermutas BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS farmaciaoperacoes BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS farmaciaavaliacao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS farmaciarelatorios BOOLEAN NOT NULL DEFAULT FALSE;
