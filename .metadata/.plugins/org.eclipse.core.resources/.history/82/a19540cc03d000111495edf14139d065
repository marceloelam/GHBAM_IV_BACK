-- Voz da chamada no painel por unidade (id do catálogo em PainelVozService).
-- NULL = voz padrão do servidor (ghbam.painel.voz.padrao).
ALTER TABLE IF EXISTS unidade_video_config
    ADD COLUMN IF NOT EXISTS vozpainel VARCHAR(40);
