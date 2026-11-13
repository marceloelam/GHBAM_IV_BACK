-- Multidisciplinar: Fonoaudiologia, Psicologia, Nutrição, Assistência Social e Fisioterapia passam a exigir,
-- além do perfil da categoria, um acesso de menu próprio (alterável na tela de servidores e pela coordenação).
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmultifono         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmultipsicologia   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmultinutricao     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmultiassistencia  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pmultifisioterapia BOOLEAN NOT NULL DEFAULT FALSE;

-- Ninguém perde tela: quem tem o perfil já via o item.
UPDATE servidor2 s2 SET pmultifono = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s.fonoaudiologia = TRUE;
UPDATE servidor2 s2 SET pmultipsicologia = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s.psicologia = TRUE;
UPDATE servidor2 s2 SET pmultinutricao = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s.nutricao = TRUE;
UPDATE servidor2 s2 SET pmultiassistencia = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s.assistencia = TRUE;
UPDATE servidor2 s2 SET pmultifisioterapia = TRUE FROM servidor s WHERE s.matricula = s2.matricula AND s.fisioterapia = TRUE;
