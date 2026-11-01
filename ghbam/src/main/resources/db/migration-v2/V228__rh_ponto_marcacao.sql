-- Ponto eletronico por reconhecimento facial com prova de vida.
-- A hora e sempre a do servidor (nunca a do terminal). Ajuste do RH entra como
-- nova marcacao com justificativa; marcacao errada e anulada, nunca apagada.

CREATE TABLE IF NOT EXISTS rh_ponto_marcacao (
    idmarcacao BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(20) NOT NULL,
    matricula VARCHAR(40) NOT NULL,
    nome VARCHAR(160),
    tipo VARCHAR(10) NOT NULL,
    datahora TIMESTAMP NOT NULL,
    origem VARCHAR(10) NOT NULL DEFAULT 'FACIAL',
    similaridade NUMERIC(5,4),
    terminal VARCHAR(120),
    justificativa TEXT,
    usuarioajuste VARCHAR(80),
    anulada BOOLEAN NOT NULL DEFAULT FALSE,
    motivoanulacao TEXT,
    usuarioanulacao VARCHAR(80),
    dataregistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rh_ponto_matricula_datahora
    ON rh_ponto_marcacao (matricula, datahora);

CREATE INDEX IF NOT EXISTS idx_rh_ponto_unidade_datahora
    ON rh_ponto_marcacao (unidade, datahora);
