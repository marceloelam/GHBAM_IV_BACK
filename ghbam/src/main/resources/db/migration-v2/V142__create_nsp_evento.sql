-- Nucleo de Seguranca do Paciente (NSP): notificacao de incidente e evento adverso.
-- Fluxo: NOTIFICADO -> EM_ANALISE -> PLANO_ACAO -> ENCERRADO (ou CANCELADO).
-- A notificacao pode ser anonima: quando anonimo = true o sistema nao grava
-- notificante nem usuario_registro, porque notificacao punitiva mata a adesao
-- do NSP. A rastreabilidade do caso fica no protocolo, nao na pessoa.
CREATE TABLE IF NOT EXISTS nsp_evento (
    idevento BIGSERIAL PRIMARY KEY,
    protocolo VARCHAR(40) NOT NULL,
    unidade VARCHAR(20) NOT NULL,
    tipo_incidente VARCHAR(60) NOT NULL,
    classificacao VARCHAR(40),
    grau_dano VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'NOTIFICADO',
    data_ocorrencia TIMESTAMP,
    local_ocorrencia VARCHAR(120),
    turno VARCHAR(20),
    idpaciente INTEGER,
    idbam INTEGER,
    prontuario VARCHAR(40),
    nome_paciente VARCHAR(160),
    descricao TEXT NOT NULL,
    acao_imediata TEXT,
    anonimo BOOLEAN NOT NULL DEFAULT FALSE,
    notificante_nome VARCHAR(160),
    notificante_matricula VARCHAR(40),
    notificante_cargo VARCHAR(120),
    prazo_analise TIMESTAMP,
    causa_raiz TEXT,
    evitavel BOOLEAN,
    conclusao TEXT,
    encerrado_em TIMESTAMP,
    encerrado_por VARCHAR(160),
    notivisa_notificado BOOLEAN NOT NULL DEFAULT FALSE,
    notivisa_protocolo VARCHAR(60),
    data_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_registro VARCHAR(80),
    data_atualizacao TIMESTAMP,
    usuario_atualizacao VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_nsp_evento_protocolo ON nsp_evento (protocolo);
CREATE INDEX IF NOT EXISTS idx_nsp_evento_unidade_status ON nsp_evento (unidade, status);
CREATE INDEX IF NOT EXISTS idx_nsp_evento_ocorrencia ON nsp_evento (unidade, data_ocorrencia);
CREATE INDEX IF NOT EXISTS idx_nsp_evento_paciente ON nsp_evento (idpaciente);

-- Plano de acao e linha do tempo do caso no mesmo lugar: ACAO_CORRETIVA e
-- ACAO_PREVENTIVA sao o plano; COMENTARIO e MUDANCA_STATUS sao a trilha.
CREATE TABLE IF NOT EXISTS nsp_evento_acao (
    idacao BIGSERIAL PRIMARY KEY,
    idevento BIGINT NOT NULL REFERENCES nsp_evento (idevento) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    descricao TEXT NOT NULL,
    responsavel VARCHAR(160),
    prazo DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    concluida_em TIMESTAMP,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_nsp_evento_acao_evento ON nsp_evento_acao (idevento, criado_em);
CREATE INDEX IF NOT EXISTS idx_nsp_evento_acao_status ON nsp_evento_acao (status, prazo);

-- pnsp: notificar e acompanhar a propria fila da unidade.
-- pnspgestao: classificar, investigar, montar plano de acao e encerrar (o nucleo).
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pnsp BOOLEAN DEFAULT FALSE;
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pnspgestao BOOLEAN DEFAULT FALSE;
