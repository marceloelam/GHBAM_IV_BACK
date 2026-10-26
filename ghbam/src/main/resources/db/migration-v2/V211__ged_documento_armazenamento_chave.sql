-- GED: anexo pode ficar no MinIO/S3 em vez de Base64 no banco.
-- Nada e apagado: os anexos existentes continuam na coluna do Base64.
-- A coluna foi criada pelo Hibernate a partir de conteudoBase64, entao o nome
-- real e conteudobase64; a V70 antiga usava conteudo_base64. Aceita os dois.
ALTER TABLE ged_documento ADD COLUMN IF NOT EXISTS armazenamento_chave VARCHAR(255);

DO $$
DECLARE coluna text;
BEGIN
    SELECT column_name INTO coluna FROM information_schema.columns
     WHERE table_schema = current_schema() AND table_name = 'ged_documento'
       AND column_name IN ('conteudobase64', 'conteudo_base64')
     LIMIT 1;
    IF coluna IS NULL THEN
        RAISE EXCEPTION 'ged_documento sem coluna de conteudo Base64';
    END IF;

    EXECUTE format('ALTER TABLE ged_documento ALTER COLUMN %I DROP NOT NULL', coluna);
    -- Todo documento precisa ter o conteudo em algum dos dois lugares.
    ALTER TABLE ged_documento DROP CONSTRAINT IF EXISTS ck_ged_documento_conteudo;
    EXECUTE format('ALTER TABLE ged_documento ADD CONSTRAINT ck_ged_documento_conteudo CHECK (%I IS NOT NULL OR armazenamento_chave IS NOT NULL)', coluna);
END $$;
