-- Protocolos clinicos em fluxograma aplicados a um paciente (tela /base2/protocolos-fluxograma).
-- Cada linha e uma execucao: quem, onde, qual versao do protocolo, cada decisao com horario
-- e como terminou. Depois de encerrada a execucao nao muda mais (rastreabilidade).
-- A gravacao so acontece com ghbam.protocolos.gravar=true (desligada ate aprovar o conteudo).
CREATE TABLE IF NOT EXISTS protocolo_fluxo_execucao (
    id BIGSERIAL PRIMARY KEY,
    protocolo_id VARCHAR(80) NOT NULL,
    protocolo_versao VARCHAR(20) NOT NULL,
    protocolo_titulo VARCHAR(200) NOT NULL,
    idpaciente VARCHAR(40) NOT NULL,
    bam VARCHAR(40),
    unidade VARCHAR(80) NOT NULL,
    matricula VARCHAR(80) NOT NULL,
    profissional VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'EM_ANDAMENTO',
    passos JSONB NOT NULL DEFAULT '[]'::jsonb,
    desfecho VARCHAR(300),
    justificativa TEXT,
    resumo TEXT,
    iniciado_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    encerrado_em TIMESTAMP,
    CONSTRAINT protocolo_fluxo_execucao_status_ck
        CHECK (status IN ('EM_ANDAMENTO', 'CONCLUIDO', 'CRITERIO_MEDICO', 'SAIDA'))
);

CREATE INDEX IF NOT EXISTS protocolo_fluxo_execucao_paciente_idx ON protocolo_fluxo_execucao (idpaciente, iniciado_em DESC);
CREATE INDEX IF NOT EXISTS protocolo_fluxo_execucao_bam_idx ON protocolo_fluxo_execucao (bam);
CREATE INDEX IF NOT EXISTS protocolo_fluxo_execucao_unidade_idx ON protocolo_fluxo_execucao (unidade, protocolo_id, iniciado_em);
