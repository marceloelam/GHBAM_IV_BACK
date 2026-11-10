-- Pedido do NIR para um medico preencher/assinar o Resumo de Alta de um BAM.
-- Qualquer medico da unidade pode atender; a pendencia fecha sozinha quando o
-- RESUMO_ALTA do BAM e assinado (AssinaturaClinicaService).
CREATE TABLE IF NOT EXISTS resumo_alta_solicitacao (
    id                   SERIAL PRIMARY KEY,
    idbam                INTEGER      NOT NULL,
    unidade              VARCHAR(40)  NOT NULL,
    solicitado_por       VARCHAR(120),
    solicitado_matricula VARCHAR(40),
    solicitado_em        TIMESTAMP    NOT NULL DEFAULT now(),
    motivo_saida         VARCHAR(60),
    resolvido_em         TIMESTAMP
);

-- Um pedido aberto por BAM: clicar duas vezes nao duplica.
CREATE UNIQUE INDEX IF NOT EXISTS ux_resumo_alta_solicitacao_aberta
    ON resumo_alta_solicitacao (idbam) WHERE resolvido_em IS NULL;

CREATE INDEX IF NOT EXISTS ix_resumo_alta_solicitacao_unidade_aberta
    ON resumo_alta_solicitacao (unidade) WHERE resolvido_em IS NULL;
