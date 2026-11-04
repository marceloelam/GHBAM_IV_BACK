-- Ponto e banco de horas passa a ter permissao propria
-- (antes era liberado junto com pcontroleplantoes).
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS prhponto BOOLEAN NOT NULL DEFAULT FALSE;
