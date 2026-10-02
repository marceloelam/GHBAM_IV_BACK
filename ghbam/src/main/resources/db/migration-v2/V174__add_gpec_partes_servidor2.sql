-- G-PEC (modulo de Atencao Primaria do GHBAM): permissao por parte do modulo,
-- exibidas no card "G-PEC" de /servidores2. Cada parte tambem libera o perfil de
-- API correspondente (ver SecurityPermissionService: paps/papsacs/papsvacina/papsgestao).
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecatendimentos BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpeccidadaos BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecagenda BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecvacinacao BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecterritorio BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecregistros BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecacompanhamento BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecrelatorios BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpectransmissao BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecconfiguracao BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gpecappacs BOOLEAN DEFAULT FALSE;
