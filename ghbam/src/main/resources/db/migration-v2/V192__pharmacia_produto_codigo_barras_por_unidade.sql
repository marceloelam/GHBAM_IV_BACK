-- Catalogo de produtos e por unidade: o mesmo codigo de barras (a mesma caixa
-- do fabricante) pode existir no catalogo de varias unidades, mas nao duas
-- vezes na mesma unidade. Troca a unicidade global por (codigobarras, unidade).
DO $$
DECLARE
    restricao text;
BEGIN
    FOR restricao IN
        SELECT c.conname
          FROM pg_constraint c
         WHERE c.conrelid = 'pharmacia_produto'::regclass
           AND c.contype = 'u'
           AND c.conkey = ARRAY[(SELECT attnum FROM pg_attribute
                                  WHERE attrelid = 'pharmacia_produto'::regclass AND attname = 'codigobarras')]::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE pharmacia_produto DROP CONSTRAINT %I', restricao);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pharmacia_produto_codigobarras_unidade
    ON pharmacia_produto (codigobarras, unidade_sigla)
    WHERE codigobarras IS NOT NULL;
