-- Auditoria estruturada de TODA chamada a LLM feita pelo backend (gateway AcessoIa).
-- O contexto clinico enviado ao modelo NAO e gravado (so o hash SHA-256): o prontuario
-- ja esta no banco e duplicar aqui so aumentaria a superficie de dado sensivel.
-- Pergunta do profissional e resposta da IA sao gravadas para rastreabilidade medico-legal.
CREATE TABLE IF NOT EXISTS ia_chamada_auditoria (
    id                  BIGSERIAL PRIMARY KEY,
    criado_em           TIMESTAMP    NOT NULL DEFAULT now(),
    origem              VARCHAR(80)  NOT NULL,
    matricula           VARCHAR(40),
    usuario_nome        VARCHAR(120),
    unidade             VARCHAR(80),
    request_id          VARCHAR(80),
    paciente_id         INTEGER,
    bam                 VARCHAR(20),
    provedor            VARCHAR(20),
    modelo              VARCHAR(120),
    status              VARCHAR(20)  NOT NULL,
    tempo_ms            BIGINT       NOT NULL,
    tokens_entrada      INTEGER,
    tokens_saida        INTEGER,
    qtd_mensagens       INTEGER,
    hash_contexto       VARCHAR(64),
    hash_resposta       VARCHAR(64),
    pergunta            TEXT,
    resposta            TEXT,
    erro                VARCHAR(240)
);

CREATE INDEX IF NOT EXISTS idx_ia_auditoria_criado_em ON ia_chamada_auditoria (criado_em);
CREATE INDEX IF NOT EXISTS idx_ia_auditoria_matricula ON ia_chamada_auditoria (matricula, criado_em);
CREATE INDEX IF NOT EXISTS idx_ia_auditoria_paciente  ON ia_chamada_auditoria (paciente_id, criado_em);
