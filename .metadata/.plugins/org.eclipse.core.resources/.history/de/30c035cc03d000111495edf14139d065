-- Remocao do modulo de criticas do Faturamento SUS (tela /faturamento-sus/criticas).
-- O resultado do motor de validacao passa a viver apenas no status/semaforo da
-- producao (afaturar.status e afaturar.possui_critica); nao ha mais registro de
-- critica persistido, nem tela para resolve-las.
--
-- DROP definitivo: as criticas ja registradas (inclusive as justificativas de RN
-- sem CNS) sao descartadas junto com a tabela, conforme decidido na remocao do
-- modulo. Os indices idx_faturamento_critica_afaturar e
-- idx_faturamento_critica_status caem junto com a tabela.
DROP TABLE IF EXISTS faturamento_critica;
