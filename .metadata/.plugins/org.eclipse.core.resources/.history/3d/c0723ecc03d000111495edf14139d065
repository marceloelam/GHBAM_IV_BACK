ALTER TABLE classificacao_ghbam_item
    DROP CONSTRAINT IF EXISTS classificacao_ghbam_item_codigo_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_classificacao_ghbam_codigo_versao
    ON classificacao_ghbam_item (codigo, versao);
