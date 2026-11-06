-- Decisao da vigilancia sobre cada caso de uma regra. So recebe INSERT: a linha mais recente de
-- (regra_id, idbam) vale e as anteriores ficam como historico. Sem linha = caso suspeito.
-- Sem FK em regra_id: excluir a regra nao pode apagar a decisao; doenca guarda o nome da epoca.
CREATE TABLE IF NOT EXISTS vigilancia_caso_classificacao (
    id            BIGSERIAL PRIMARY KEY,
    regra_id      INTEGER      NOT NULL,
    doenca        VARCHAR(120) NOT NULL,
    unidade       VARCHAR(60)  NOT NULL,
    idbam         INTEGER      NOT NULL,
    idpaciente    INTEGER,
    classificacao VARCHAR(20)  NOT NULL CHECK (classificacao IN ('SUSPEITO', 'PROVAVEL', 'CONFIRMADO', 'DESCARTADO')),
    criterio      VARCHAR(30)  NOT NULL DEFAULT '',
    justificativa TEXT         NOT NULL,
    matricula     VARCHAR(30)  NOT NULL,
    profissional  VARCHAR(200) NOT NULL DEFAULT '',
    criado_em     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vigilancia_caso_regra_bam
    ON vigilancia_caso_classificacao (regra_id, idbam, id DESC);
