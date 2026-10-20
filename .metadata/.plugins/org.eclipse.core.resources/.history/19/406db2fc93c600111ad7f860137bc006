-- Corrige a constraint NOT NULL da coluna prontuario_idprontuario na tabela bam
-- O campo prontuario foi desativado na aplicação mas a constraint permaneceu no banco

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'bam'
          AND column_name = 'prontuario_idprontuario'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE bam ALTER COLUMN prontuario_idprontuario DROP NOT NULL;
        RAISE NOTICE 'Constraint NOT NULL removida da coluna prontuario_idprontuario.';
    ELSE
        RAISE NOTICE 'Coluna prontuario_idprontuario já permite NULL, nenhuma alteração necessária.';
    END IF;
END $$;
