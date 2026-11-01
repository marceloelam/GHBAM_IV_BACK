-- Rastreabilidade da CME (RDC 15/2012): ciclo de esterilizacao -> pacote
-- etiquetado -> paciente. Os registros livres de evidencia_assistencial
-- (modulo CME) continuam existindo; estas tabelas ligam cada pacote ao
-- ciclo e ao paciente para permitir recolhimento quando um indicador falha.

CREATE TABLE IF NOT EXISTS cme_ciclo (
    idciclo BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    equipamento VARCHAR(80) NOT NULL,
    numerociclo VARCHAR(40) NOT NULL,
    metodo VARCHAR(40) NOT NULL,
    datainicio TIMESTAMP,
    datafim TIMESTAMP,
    temperatura NUMERIC(6,2),
    pressao NUMERIC(6,2),
    tempoexposicaomin INTEGER,
    indicadorfisico VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    indicadorquimico VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    indicadorbiologico VARCHAR(20) NOT NULL DEFAULT 'NAO_REALIZADO',
    loteindicadorbiologico VARCHAR(60),
    status VARCHAR(20) NOT NULL DEFAULT 'EM_ANALISE',
    motivoreprovacao TEXT,
    observacao TEXT,
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuarioregistro VARCHAR(80),
    dataliberacao TIMESTAMP,
    usuarioliberacao VARCHAR(80),
    dataatualizacao TIMESTAMP,
    usuarioatualizacao VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cme_ciclo_unidade_equipamento_numero
    ON cme_ciclo (unidade, equipamento, numerociclo);

CREATE INDEX IF NOT EXISTS idx_cme_ciclo_unidade_status
    ON cme_ciclo (unidade, status);

CREATE TABLE IF NOT EXISTS cme_pacote (
    idpacote BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(40),
    idciclo BIGINT NOT NULL REFERENCES cme_ciclo (idciclo),
    unidade VARCHAR(20) NOT NULL,
    descricao VARCHAR(160) NOT NULL,
    quantidade INTEGER NOT NULL DEFAULT 1,
    contemimplante BOOLEAN NOT NULL DEFAULT FALSE,
    validade DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ESTERILIZADO',
    setordestino VARCHAR(80),
    idpaciente INTEGER,
    prontuario VARCHAR(40),
    nomepaciente VARCHAR(160),
    referenciauso VARCHAR(80),
    datautilizacao TIMESTAMP,
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuarioregistro VARCHAR(80),
    dataatualizacao TIMESTAMP,
    usuarioatualizacao VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cme_pacote_codigo
    ON cme_pacote (codigo);

CREATE INDEX IF NOT EXISTS idx_cme_pacote_ciclo
    ON cme_pacote (idciclo);

CREATE INDEX IF NOT EXISTS idx_cme_pacote_unidade_status
    ON cme_pacote (unidade, status);

CREATE INDEX IF NOT EXISTS idx_cme_pacote_paciente
    ON cme_pacote (idpaciente);

-- Historico imutavel de cada passo do pacote e do ciclo (quem, quando, o que).
CREATE TABLE IF NOT EXISTS cme_evento (
    idevento BIGSERIAL PRIMARY KEY,
    idciclo BIGINT NOT NULL REFERENCES cme_ciclo (idciclo),
    idpacote BIGINT REFERENCES cme_pacote (idpacote),
    evento VARCHAR(40) NOT NULL,
    statusanterior VARCHAR(20),
    statusnovo VARCHAR(20),
    detalhe TEXT,
    usuario VARCHAR(80),
    dataevento TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cme_evento_ciclo
    ON cme_evento (idciclo);

CREATE INDEX IF NOT EXISTS idx_cme_evento_pacote
    ON cme_evento (idpacote);
