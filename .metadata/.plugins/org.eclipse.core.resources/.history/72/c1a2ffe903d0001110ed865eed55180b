-- Envio automatico e generico de sumarios clinicos em FHIR para repositorios
-- externos (RNDS, RISAH/SMS-SP, outros municipais). Nao acoplado ao modelo de
-- credencial do DATASUS (integracao_sus_credencial) porque o modelo de
-- autenticacao e o proposito sao distintos.

CREATE TABLE IF NOT EXISTS fhir_envio_destino (
    idfhirenviodestino SERIAL PRIMARY KEY,
    unidade VARCHAR(25) NOT NULL,
    finalidade VARCHAR(40) NOT NULL DEFAULT 'SUMARIO_ALTA',
    ambiente VARCHAR(20) NOT NULL DEFAULT 'HOMOLOGACAO',
    endpoint_base VARCHAR(300),
    tipo_autenticacao VARCHAR(20) NOT NULL DEFAULT 'NENHUMA', -- NENHUMA | BEARER_TOKEN | BASIC | API_KEY_HEADER
    credencial_criptografada TEXT,
    api_key_header_nome VARCHAR(80),
    ativo BOOLEAN NOT NULL DEFAULT FALSE, -- disparo real desligado ate haver endpoint/credencial real
    criado_por VARCHAR(80),
    atualizado_por VARCHAR(80),
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fhir_envio_destino_unidade_finalidade
    ON fhir_envio_destino (lower(unidade), finalidade);

-- Fila/log de envio: 1 linha por documento assinado, idempotente.
CREATE TABLE IF NOT EXISTS fhir_envio_fila (
    idfhirenviofila BIGSERIAL PRIMARY KEY,
    tipo_documento VARCHAR(60) NOT NULL,
    iddocumento INTEGER NOT NULL,
    idbam INTEGER,
    idpaciente INTEGER,
    idassinatura INTEGER REFERENCES assinatura_clinica (idassinatura),
    unidade VARCHAR(25) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE', -- PENDENTE | ENVIADO | ERRO | ERRO_DEFINITIVO | NAO_CONFIGURADO | DESATIVADO
    payload_fhir TEXT,
    status_http INTEGER,
    resposta_http TEXT,
    tentativas INTEGER NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMP,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP,
    enviado_em TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fhir_envio_fila_documento
    ON fhir_envio_fila (tipo_documento, iddocumento);
CREATE INDEX IF NOT EXISTS idx_fhir_envio_fila_status_retry
    ON fhir_envio_fila (status, proxima_tentativa_em);
CREATE INDEX IF NOT EXISTS idx_fhir_envio_fila_unidade
    ON fhir_envio_fila (unidade, criado_em DESC);

-- Permissao para configurar destino/credencial e reenviar manualmente.
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pfhirsumarioalta BOOLEAN DEFAULT FALSE;
