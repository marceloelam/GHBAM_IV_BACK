-- Permissoes do Centro Cirurgico. Ficam em servidor2 com o prefixo "p" das
-- demais permissoes granulares; o prefixo "k" e reservado as chaves de setor
-- que vivem na tabela servidor.
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcentrocirurgico BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgiaagendar BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgiaexecutar BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgiadescrever BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgiaanestesia BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgiasuspender BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgiaopme BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgiasalas BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pcirurgialistaespera BOOLEAN DEFAULT FALSE;
