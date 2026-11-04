-- Reserva de leito pelo NIR. O historico fica na tabela: encerrar muda o status, nunca apaga.
-- Reserva vencida deixa de valer pelo expira_em, sem rotina agendada.
CREATE TABLE IF NOT EXISTS leito_reserva (
    id BIGSERIAL PRIMARY KEY,
    idleito INTEGER NOT NULL,
    bam INTEGER NOT NULL,
    unidade VARCHAR(35) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'ATIVA'
        CHECK (status IN ('ATIVA', 'CONSUMIDA', 'CANCELADA', 'EXPIRADA')),
    criada_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    criada_por VARCHAR(80) NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    encerrada_em TIMESTAMP,
    encerrada_por VARCHAR(80),
    motivo_encerramento VARCHAR(200)
);

-- Uma reserva ativa por leito e uma por BAM.
CREATE UNIQUE INDEX IF NOT EXISTS ux_leito_reserva_leito_ativa ON leito_reserva (idleito) WHERE status = 'ATIVA';
CREATE UNIQUE INDEX IF NOT EXISTS ux_leito_reserva_bam_ativa ON leito_reserva (bam) WHERE status = 'ATIVA';
CREATE INDEX IF NOT EXISTS ix_leito_reserva_unidade_status ON leito_reserva (unidade, status);
