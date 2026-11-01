-- CME: numero do ciclo passa a reiniciar todo dia em cada autoclave
-- ("AUTOCLAVE 01, 30/10, ciclo 3"), como no impresso do equipamento.
-- datareferencia e a data local do ciclo gravada pela aplicacao (DATE, sem
-- conversao de fuso), base da numeracao e do filtro "Hoje".

ALTER TABLE cme_ciclo
    ADD COLUMN IF NOT EXISTS datareferencia DATE;

UPDATE cme_ciclo
   SET datareferencia = CAST(datainicio AS DATE)
 WHERE datareferencia IS NULL;

DROP INDEX IF EXISTS uk_cme_ciclo_unidade_equipamento_numero;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cme_ciclo_unidade_equipamento_dia_numero
    ON cme_ciclo (unidade, equipamento, datareferencia, numerociclo);

CREATE INDEX IF NOT EXISTS idx_cme_ciclo_unidade_dia
    ON cme_ciclo (unidade, datareferencia);
