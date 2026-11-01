-- Pedir material ao almoxarifado passa a exigir permissao propria
-- (antes era liberado a qualquer usuario logado).
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS palmoxpedir BOOLEAN NOT NULL DEFAULT FALSE;
