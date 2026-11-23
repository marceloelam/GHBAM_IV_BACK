-- Validação das condutas clínicas por médico responsável (22/11/2026).
-- Quem valida vem do token (matrícula, nome, CRM), nunca do cliente. hash_conteudo é a
-- "impressão digital" do texto da conduta: se o texto mudar, a validação deixa de valer.
-- Revogar não apaga: marca revogado_em/revogado_por, para manter o histórico.
CREATE TABLE IF NOT EXISTS conduta_validacao (
    id BIGSERIAL PRIMARY KEY,
    conduta_uuid VARCHAR(64) NOT NULL,
    titulo VARCHAR(200),
    hash_conteudo VARCHAR(64) NOT NULL,
    matricula VARCHAR(80) NOT NULL,
    nome VARCHAR(200),
    crm VARCHAR(40) NOT NULL,
    validado_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    revogado_em TIMESTAMP,
    revogado_por VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_conduta_validacao_ativa ON conduta_validacao (conduta_uuid) WHERE revogado_em IS NULL;
