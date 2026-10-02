-- AfaturarService.salvarFatura evita duplicidade via "check-then-insert" (buscarPorOrigem
-- antes de gravar), mas isso e so protecao de aplicacao: duas requisicoes concorrentes com
-- o mesmo (origem_modulo, origem_id) ainda podem gravar produção faturavel duplicada.
--
-- Este bloco e defensivo de proposito: so cria o indice UNIQUE quando nao ha nenhuma
-- duplicata hoje. Se houver, ele NAO falha o boot (apenas avisa) - travar o Flyway numa
-- migration de indice derrubaria o sistema hospitalar inteiro por causa de um dado antigo
-- que ninguem pediu para corrigir agora. Resolver as duplicatas e decisao separada, que
-- exige olhar cada caso (qual das duas linhas e a valida, se ja foi faturada/transmitida).
DO $$
DECLARE
    duplicatas integer;
BEGIN
    SELECT count(*) INTO duplicatas FROM (
        SELECT origem_modulo, origem_id
        FROM afaturar
        WHERE origem_modulo IS NOT NULL AND origem_modulo <> ''
          AND origem_id IS NOT NULL
        GROUP BY origem_modulo, origem_id
        HAVING count(*) > 1
    ) grupos_duplicados;

    IF duplicatas = 0 THEN
        CREATE UNIQUE INDEX IF NOT EXISTS ux_afaturar_origem
            ON afaturar (origem_modulo, origem_id)
            WHERE origem_modulo IS NOT NULL AND origem_modulo <> '' AND origem_id IS NOT NULL;
        RAISE NOTICE 'afaturar: indice UNIQUE (origem_modulo, origem_id) criado.';
    ELSE
        RAISE WARNING 'afaturar: % grupo(s) de origem duplicada encontrados; indice UNIQUE NAO criado nesta execucao. Resolver as duplicatas (conferir qual linha e valida) e criar o indice numa migration futura.', duplicatas;
    END IF;
END $$;
