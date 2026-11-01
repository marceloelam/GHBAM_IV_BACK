-- Folha de ponto completa: ocorrencias de frequencia (ferias, atestados, licencas,
-- abonos, faltas), feriados locais e fechamento mensal por unidade.
-- Nada e apagado: ocorrencia errada e anulada com motivo; reabertura de mes guarda quem e por que.

CREATE TABLE IF NOT EXISTS rh_ponto_ocorrencia (
    idocorrencia BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    matricula VARCHAR(40) NOT NULL,
    nome VARCHAR(160),
    tipo VARCHAR(30) NOT NULL,
    datainicio DATE NOT NULL,
    datafim DATE NOT NULL,
    minutos INTEGER,
    periodoaquisitivo VARCHAR(20),
    documento VARCHAR(80),
    observacao TEXT,
    usuario VARCHAR(80),
    anulada BOOLEAN NOT NULL DEFAULT FALSE,
    motivoanulacao TEXT,
    usuarioanulacao VARCHAR(80),
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rh_ponto_ocorr_matricula_periodo
    ON rh_ponto_ocorrencia (matricula, datainicio, datafim);

CREATE INDEX IF NOT EXISTS idx_rh_ponto_ocorr_unidade_periodo
    ON rh_ponto_ocorrencia (unidade, datainicio, datafim);

-- Feriados nacionais sao calculados no codigo; aqui ficam os estaduais, municipais
-- e pontos facultativos. unidade nula = vale para todas.
CREATE TABLE IF NOT EXISTS rh_feriado (
    idferiado BIGSERIAL PRIMARY KEY,
    data DATE NOT NULL,
    descricao VARCHAR(120) NOT NULL,
    tipo VARCHAR(20) NOT NULL DEFAULT 'MUNICIPAL',
    unidade VARCHAR(20),
    usuario VARCHAR(80),
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rh_feriado_data_unidade
    ON rh_feriado (data, COALESCE(unidade, ''));

CREATE TABLE IF NOT EXISTS rh_ponto_fechamento (
    idfechamento BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    mes VARCHAR(7) NOT NULL,
    fechadopor VARCHAR(80),
    datafechamento TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    reabertopor VARCHAR(80),
    datareabertura TIMESTAMP,
    motivoreabertura TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rh_ponto_fechamento_ativo
    ON rh_ponto_fechamento (unidade, mes) WHERE ativo;
