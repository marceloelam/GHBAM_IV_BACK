CREATE TABLE IF NOT EXISTS aps_cadastro_territorial (
  id BIGSERIAL PRIMARY KEY, unidade VARCHAR(30) NOT NULL, microarea VARCHAR(30) NOT NULL,
  tipo_registro VARCHAR(20) NOT NULL, nome VARCHAR(120) NOT NULL, cpf VARCHAR(14), cns VARCHAR(20), data_nascimento DATE,
  nome_responsavel VARCHAR(120), logradouro VARCHAR(160), numero VARCHAR(20), bairro VARCHAR(80), cep VARCHAR(10), telefone VARCHAR(30),
  id_responsavel_familiar BIGINT, equipe_referencia VARCHAR(120), id_acs INTEGER, ativo BOOLEAN NOT NULL DEFAULT TRUE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_aps_cadastro_tipo CHECK (tipo_registro IN ('IMOVEL', 'FAMILIA', 'CIDADAO'))
);
CREATE INDEX IF NOT EXISTS idx_aps_cadastro_unidade_microarea ON aps_cadastro_territorial (unidade, microarea);
CREATE INDEX IF NOT EXISTS idx_aps_cadastro_cpf ON aps_cadastro_territorial (cpf);
CREATE INDEX IF NOT EXISTS idx_aps_cadastro_cns ON aps_cadastro_territorial (cns);
