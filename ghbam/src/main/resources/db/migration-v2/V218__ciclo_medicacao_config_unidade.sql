-- Por unidade (sigla): a farmacia registra a dispensacao no ciclo de medicacao?
-- Sem linha = sim (padrao). Com false, a etapa de dispensacao some do ciclo e da fila.
CREATE TABLE IF NOT EXISTS ciclo_medicacao_config (
    unidade VARCHAR(20) PRIMARY KEY,
    farmacia_dispensa BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_por VARCHAR(120),
    atualizado_em TIMESTAMP
);
