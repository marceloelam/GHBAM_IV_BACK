-- Farmacia: PDF da operacao pode ficar no MinIO/S3 em vez de Base64 no banco.
-- Nada e apagado: os documentos existentes continuam em conteudo_base64.
ALTER TABLE pharmacia_operacao_documento ADD COLUMN IF NOT EXISTS armazenamento_chave VARCHAR(255);
ALTER TABLE pharmacia_operacao_documento ALTER COLUMN conteudo_base64 DROP NOT NULL;

ALTER TABLE pharmacia_operacao_documento DROP CONSTRAINT IF EXISTS ck_pharmacia_documento_conteudo;
ALTER TABLE pharmacia_operacao_documento ADD CONSTRAINT ck_pharmacia_documento_conteudo
    CHECK (conteudo_base64 IS NOT NULL OR armazenamento_chave IS NOT NULL);
