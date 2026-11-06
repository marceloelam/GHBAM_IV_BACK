-- Resultado infeccioso lido do PDF do laudo, so para a vigilancia. Separado de pedidoresultadolaboratorio_item:
-- item sem LOINC trava o laudo na fila RNDS. Leitura automatica: a decisao sobre o caso e sempre da vigilancia.
CREATE TABLE IF NOT EXISTS laboratorio_resultado_infeccioso (
    id                BIGSERIAL PRIMARY KEY,
    idpedidoresultado INTEGER      NOT NULL,
    bam               INTEGER      NOT NULL,
    nome_exame        VARCHAR(120) NOT NULL,
    resultado         VARCHAR(20)  NOT NULL CHECK (resultado IN ('REAGENTE', 'NAO_REAGENTE')),
    criado_em         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_laboratorio_resultado_infeccioso_bam ON laboratorio_resultado_infeccioso (bam);
