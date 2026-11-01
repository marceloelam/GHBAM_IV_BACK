-- Chat dos profissionais liberado por padrão para todos os usuários.
-- Quem não deve usar é desligado individualmente em Cadastro de servidores (servidores2).
ALTER TABLE servidor2 ALTER COLUMN chatprofissionais SET DEFAULT TRUE;
UPDATE servidor2 SET chatprofissionais = TRUE WHERE chatprofissionais IS DISTINCT FROM TRUE;
