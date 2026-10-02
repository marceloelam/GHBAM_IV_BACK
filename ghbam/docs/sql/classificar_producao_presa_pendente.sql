-- Corrige producao legada presa em PENDENTE_CLASSIFICACAO.
--
-- POR QUE NAO E UMA MIGRATION: o volume de linhas afetadas faz o UPDATE passar dos
-- 120s de socketTimeout do datasource (ver quarkus.datasource.jdbc.url). Como
-- migration, o Flyway derrubava o start inteiro da aplicacao (SQLSTATE 08006).
-- Correcao de dado em massa nao pode bloquear a subida do backend.
--
-- COMO USAR: rode o bloco abaixo repetidamente ate ele reportar 0 linhas
-- atualizadas. Pode rodar com o sistema no ar:
--   - LIMIT 2000 mantem cada execucao curta;
--   - ORDER BY idafaturar trava sempre na mesma ordem (evita deadlock 40P01 contra
--     o proprio backend, que tambem faz UPDATE em afaturar);
--   - FOR UPDATE SKIP LOCKED pula linhas que a aplicacao esteja usando naquele
--     instante, em vez de esperar por elas (a proxima execucao as pega).
--
-- REGRA: identica a FaturamentoProducaoService.classificarInstrumento
--   pa_tpreg 02/03             -> BPA_I
--   pa_tpreg 01                -> BPA_C
--   sem valor SIGTAP           -> NAO_FATURAVEL
--   com valor, tipo indefinido -> BPA_I se o CNS do paciente tem 15 digitos, senao BPA_C
--
-- classificacao_manual = true faz o reprocessamento respeitar o instrumento gravado
-- em vez de recalcular. O status vai para PRODUCAO_COM_CRITICA quando ja existe
-- critica aberta e APTO_FATURAMENTO caso contrario - mesmo criterio do motor, que
-- revalida e corrige no proximo reprocessamento.

-- ============================================================================
-- 1. Quantas faltam (rode antes e depois para acompanhar)
-- ============================================================================
SELECT count(*) AS pendentes_restantes
  FROM afaturar
 WHERE status = 'PENDENTE_CLASSIFICACAO';

-- ============================================================================
-- 2. Um lote. Repita ate "UPDATE 0".
-- ============================================================================
WITH alvo AS (
    SELECT idafaturar
      FROM afaturar
     WHERE status = 'PENDENTE_CLASSIFICACAO'
     ORDER BY idafaturar
     LIMIT 2000
       FOR UPDATE SKIP LOCKED
)
UPDATE afaturar a
   SET instrumento_registro = CASE
           WHEN btrim(coalesce(a.pa_tpreg, '')) IN ('02', '2', '03', '3') THEN 'BPA_I'
           WHEN btrim(coalesce(a.pa_tpreg, '')) IN ('01', '1') THEN 'BPA_C'
           WHEN coalesce(a.pa_sa, 0) <= 0 AND coalesce(a.pa_sp, 0) <= 0
                AND coalesce(a.pa_sh, 0) <= 0 THEN 'NAO_FATURAVEL'
           WHEN length(regexp_replace(coalesce(a.cnsdopciente, ''), '\D', '', 'g')) = 15 THEN 'BPA_I'
           ELSE 'BPA_C'
       END,
       classificacao_manual = CASE
           WHEN coalesce(a.pa_sa, 0) <= 0 AND coalesce(a.pa_sp, 0) <= 0
                AND coalesce(a.pa_sh, 0) <= 0 THEN FALSE
           ELSE TRUE
       END,
       status = CASE
           WHEN coalesce(a.pa_sa, 0) <= 0 AND coalesce(a.pa_sp, 0) <= 0
                AND coalesce(a.pa_sh, 0) <= 0 THEN 'NAO_FATURAVEL'
           WHEN EXISTS (SELECT 1
                          FROM faturamento_critica c
                         WHERE c.idafaturar = a.idafaturar
                           AND upper(coalesce(c.status, '')) = 'ABERTA') THEN 'PRODUCAO_COM_CRITICA'
           ELSE 'APTO_FATURAMENTO'
       END,
       dataalteracao = now(),
       usuarioalterador = 'script-classificacao'
  FROM alvo
 WHERE a.idafaturar = alvo.idafaturar;

-- ============================================================================
-- 3. Producao com status ja resolvido mas instrumento vazio/pendente. Sem isto a
--    exportacao BPA nunca a encontra. Tambem em lotes, ate "UPDATE 0".
-- ============================================================================
WITH alvo_instrumento AS (
    SELECT idafaturar
      FROM afaturar
     WHERE coalesce(instrumento_registro, '') IN ('', 'PENDENTE_CLASSIFICACAO')
       AND status NOT IN ('PENDENTE_CLASSIFICACAO', 'NAO_FATURAVEL')
       AND (coalesce(pa_sa, 0) > 0 OR coalesce(pa_sp, 0) > 0 OR coalesce(pa_sh, 0) > 0)
     ORDER BY idafaturar
     LIMIT 2000
       FOR UPDATE SKIP LOCKED
)
UPDATE afaturar a
   SET instrumento_registro = CASE
           WHEN btrim(coalesce(a.pa_tpreg, '')) IN ('02', '2', '03', '3') THEN 'BPA_I'
           WHEN btrim(coalesce(a.pa_tpreg, '')) IN ('01', '1') THEN 'BPA_C'
           WHEN length(regexp_replace(coalesce(a.cnsdopciente, ''), '\D', '', 'g')) = 15 THEN 'BPA_I'
           ELSE 'BPA_C'
       END,
       classificacao_manual = TRUE,
       dataalteracao = now(),
       usuarioalterador = 'script-classificacao'
  FROM alvo_instrumento
 WHERE a.idafaturar = alvo_instrumento.idafaturar;

-- ============================================================================
-- 4. Conferencia de uma producao especifica
-- ============================================================================
-- SELECT idafaturar, status, instrumento_registro, classificacao_manual, exportado,
--        idcompetencia, unidade, procedimento, pa_tpreg, pa_sa, pa_sp, pa_sh,
--        length(regexp_replace(coalesce(cnsdopciente,''),'\D','','g')) AS cns_digitos
--   FROM afaturar
--  WHERE idafaturar IN (4075672, 4075673);
