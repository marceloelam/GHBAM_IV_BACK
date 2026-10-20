-- O limite validado vale para todas as unidades; registra-se de qual unidade e
-- quem validou (e quem fez cada acao do historico).
ALTER TABLE cdss_regra_dose ADD COLUMN IF NOT EXISTS validado_unidade VARCHAR(25);
ALTER TABLE cdss_regra_dose_historico ADD COLUMN IF NOT EXISTS por_unidade VARCHAR(25);
