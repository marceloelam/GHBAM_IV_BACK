-- Configuracao da IA de imagem (MONAI) por unidade, editavel na tela /unidades
-- (Cad_unidades). Fica junto da config de PACS que ja existe em configuracao_unidade.
-- Tudo desligado por padrao; so vale quando o admin preencher e ligar na unidade.
-- A entity e @Entity(name = "configurarunidade") sem @Table: a tabela real e
-- "configurarunidade" (nome da entity vira o nome da tabela).
ALTER TABLE configurarunidade ADD COLUMN IF NOT EXISTS monai_habilitado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE configurarunidade ADD COLUMN IF NOT EXISTS monai_url VARCHAR(120);
ALTER TABLE configurarunidade ADD COLUMN IF NOT EXISTS orthanc_url VARCHAR(200);
