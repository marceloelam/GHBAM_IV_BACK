-- Remover documento do GED passa a ser permissao propria: consultar o anexo nao
-- implica poder apaga-lo. A coluna ged (acesso ao modulo) veio na V70.
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS gedremover BOOLEAN NOT NULL DEFAULT FALSE;
