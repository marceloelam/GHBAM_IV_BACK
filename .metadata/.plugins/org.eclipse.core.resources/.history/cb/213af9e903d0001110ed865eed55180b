-- Adicionar coluna especialidade na tabela servidor2
-- Esta migration adiciona o campo especialidade para armazenar a especialidade médica do usuário

-- Verifica se a coluna já existe antes de adicionar
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name='servidor2' 
        AND column_name='especialidade'
    ) THEN
        ALTER TABLE servidor2 ADD COLUMN especialidade VARCHAR(55);
        RAISE NOTICE 'Coluna especialidade adicionada com sucesso na tabela servidor2';
    ELSE
        RAISE NOTICE 'Coluna especialidade já existe na tabela servidor2';
    END IF;
END $$;

-- Comentário na coluna para documentação
COMMENT ON COLUMN servidor2.especialidade IS 'Especialidade médica ou profissional do usuário';
