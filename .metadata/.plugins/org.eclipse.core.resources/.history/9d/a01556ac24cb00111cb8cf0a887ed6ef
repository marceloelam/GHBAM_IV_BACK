-- Rede de seguranca da idempotencia por origem.
--
-- A trava principal e o AfaturarService.salvarFatura, que consulta buscarPorOrigem e
-- devolve a producao existente em vez de criar outra. Este indice cobre o que codigo
-- nenhum cobre: duas requisicoes simultaneas do mesmo lancamento (duplo clique de
-- verdade), em que as duas transacoes consultam antes de qualquer uma gravar.
--
-- Parcial de proposito: producao sem chave de origem (as telas que ainda nao passam
-- origem, e todo o historico anterior a esta versao) continua podendo repetir - a
-- restricao so vale onde ha chave para comparar. Em Postgres um indice unico comum ja
-- ignoraria os nulos, mas o WHERE deixa a intencao explicita e mantem o indice pequeno.
--
-- Conferido antes de criar: nenhum grupo (origem_modulo, origem_id) duplicado na base.
CREATE UNIQUE INDEX IF NOT EXISTS ux_afaturar_origem
    ON afaturar (origem_modulo, origem_id)
    WHERE origem_modulo IS NOT NULL AND origem_id IS NOT NULL;
