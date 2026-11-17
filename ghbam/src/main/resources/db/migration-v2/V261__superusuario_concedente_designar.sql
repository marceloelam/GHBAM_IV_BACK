-- Designado que tambem pode designar/cancelar quem concede Super usuario.
-- So o responsavel da plataforma liga ou desliga esta marca.
ALTER TABLE superusuario_concedente ADD COLUMN IF NOT EXISTS pode_designar BOOLEAN NOT NULL DEFAULT FALSE;
