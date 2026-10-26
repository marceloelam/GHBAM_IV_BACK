-- GHBAM APS (Atencao Primaria / substituto do PEC e-SUS nas UBS/ESF).
-- Permissoes finas no mesmo modelo das demais (colunas booleanas em servidor2):
--   aps        -> profissional da UBS/ESF: cidadao, lista de atendimento, escuta, SOAP, agenda, fichas CDS
--   apsacs     -> ACS/ACE: territorio, cadastros domiciliar/individual e visita domiciliar
--   apsvacina  -> sala de vacina: aplicacao, transcricao, aprazamento, lotes
--   apsgestao  -> coordenacao: equipes/INE, lotacoes, relatorios, acompanhamentos e transmissao LEDI
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS aps BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS apsacs BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS apsvacina BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS apsgestao BOOLEAN DEFAULT FALSE;
