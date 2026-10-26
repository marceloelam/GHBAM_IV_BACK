-- Visitantes: a foto pode ficar no MinIO/S3 em vez de Base64 no banco.
-- Nada e apagado: as fotos existentes continuam em fotovisitante.
ALTER TABLE visitantes ADD COLUMN IF NOT EXISTS armazenamento_chave VARCHAR(255);
