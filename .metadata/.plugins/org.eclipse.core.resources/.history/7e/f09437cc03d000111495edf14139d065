-- Acesso ao modulo de Cautela e permissao propria do cadastro (bloco NIR), nao
-- deriva de perfil: quem regula leito nao necessariamente controla patrimonio.
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS cautela BOOLEAN NOT NULL DEFAULT FALSE;
