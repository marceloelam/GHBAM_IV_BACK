-- Adicionar permissao de acesso a auditoria clinica na tabela servidor2

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name='servidor2'
        AND column_name='auditoriaclinica'
    ) THEN
        ALTER TABLE servidor2 ADD COLUMN auditoriaclinica BOOLEAN DEFAULT FALSE NOT NULL;
        RAISE NOTICE 'Coluna auditoriaclinica adicionada com sucesso na tabela servidor2';
    ELSE
        RAISE NOTICE 'Coluna auditoriaclinica ja existe na tabela servidor2';
    END IF;
END $$;
