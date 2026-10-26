-- Laboratorio: o PDF do laudo pode ficar no MinIO/S3 em vez de Base64 no banco.
-- Nada e apagado: os laudos existentes continuam em arquivo.
ALTER TABLE pedidoresultadolaboratorio ADD COLUMN IF NOT EXISTS armazenamento_chave VARCHAR(255);
