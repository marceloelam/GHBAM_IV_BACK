-- Adiciona colunas de flags de exames na tabela registro se ainda não existirem
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'registro'
          AND column_name = 'temlabo'
    ) THEN
        ALTER TABLE registro ADD COLUMN temlabo BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'registro'
          AND column_name = 'temrx'
    ) THEN
        ALTER TABLE registro ADD COLUMN temrx BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'registro'
          AND column_name = 'temtomo'
    ) THEN
        ALTER TABLE registro ADD COLUMN temtomo BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'registro'
          AND column_name = 'temmed'
    ) THEN
        ALTER TABLE registro ADD COLUMN temmed BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'registro'
          AND column_name = 'temenfe'
    ) THEN
        ALTER TABLE registro ADD COLUMN temenfe BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

COMMENT ON COLUMN registro.temlabo IS 'Flag que indica se há pedido de laboratório';
COMMENT ON COLUMN registro.temrx IS 'Flag que indica se há pedido de raio-x';
COMMENT ON COLUMN registro.temtomo IS 'Flag que indica se há pedido de tomografia';
COMMENT ON COLUMN registro.temmed IS 'Flag que indica se há pedido de medicação';
COMMENT ON COLUMN registro.temenfe IS 'Flag que indica se há pedido de enfermagem';
