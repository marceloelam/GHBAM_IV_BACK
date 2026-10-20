-- Adicionar colunas de permissões de menu na tabela servidor2
-- Permissões para itens do menu Administrativo e Enfermagem

DO $$ 
DECLARE
    coluna text;
BEGIN
    -- Lista das novas colunas de permissão administrativa
    FOREACH coluna IN ARRAY ARRAY[
        'aguardandoatendimento',
        'atendimentoperiodo',
        'biometria',
        'comunicacaoespecialistas',
        'correcaobam',
        'enfermariaseleitos',
        'imprimirbam',
        'limparpainel',
        'pacientesematendimento',
        'agendamentomedico',
        'cadastrarsalas',
        'cadastrarservidores',
        'cadastrarsetores',
        'cadastrarunidades',
        'auditoriaclinica',
        'correcãobam',
        'emapropriatendimento',
        'listaservidores',
        'mapapacientes',
        'permissoes',
        'resumopacientes',
        'usuariosonline',
        'chatprofissionais'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_name='servidor2' 
            AND column_name=coluna
        ) THEN
            EXECUTE format('ALTER TABLE servidor2 ADD COLUMN %I BOOLEAN DEFAULT FALSE NOT NULL', coluna);
            RAISE NOTICE 'Coluna % adicionada com sucesso na tabela servidor2', coluna;
        ELSE
            RAISE NOTICE 'Coluna % já existe na tabela servidor2', coluna;
        END IF;
    END LOOP;

    -- Lista das novas colunas de permissão de enfermagem
    FOREACH coluna IN ARRAY ARRAY[
        'pedidosexame',
        'balancohidrico',
        'ccihresumo',
        'driveenfermagem',
        'escalaenfermagem',
        'evolucaomultidisciplinar',
        'minhasassinaturas',
        'avaliacaopacienteia',
        'bulariomedico',
        'ciclomedicacao',
        'historicocompleto',
        'painelenfermagem'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_name='servidor2' 
            AND column_name=coluna
        ) THEN
            EXECUTE format('ALTER TABLE servidor2 ADD COLUMN %I BOOLEAN DEFAULT FALSE NOT NULL', coluna);
            RAISE NOTICE 'Coluna % adicionada com sucesso na tabela servidor2', coluna;
        ELSE
            RAISE NOTICE 'Coluna % já existe na tabela servidor2', coluna;
        END IF;
    END LOOP;

    -- Lista das novas colunas de permissão NIR
    FOREACH coluna IN ARRAY ARRAY[
        'biexecutivo',
        'nirrestaurarinternacao',
        'nir',
        'painelnir',
        'localcenso',
        'pesquisanir',
        'nirrelatorio',
        'setorcenso',
        'internadosvisitaimp'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_name='servidor2' 
            AND column_name=coluna
        ) THEN
            EXECUTE format('ALTER TABLE servidor2 ADD COLUMN %I BOOLEAN DEFAULT FALSE NOT NULL', coluna);
            RAISE NOTICE 'Coluna % adicionada com sucesso na tabela servidor2', coluna;
        ELSE
            RAISE NOTICE 'Coluna % já existe na tabela servidor2', coluna;
        END IF;
    END LOOP;

END $$;
