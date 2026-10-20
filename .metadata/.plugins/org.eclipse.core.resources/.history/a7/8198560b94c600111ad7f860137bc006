DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'servidor2'
          AND column_name = 'correcãobam'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM servidor2
            WHERE "correcãobam" IS TRUE
        ) THEN
            RAISE EXCEPTION
                'A coluna corrompida correcãobam possui permissões ativas; migração interrompida.';
        END IF;

        ALTER TABLE servidor2 DROP COLUMN "correcãobam";
    END IF;
END $$;
