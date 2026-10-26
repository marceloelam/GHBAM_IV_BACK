CREATE TABLE IF NOT EXISTS aps_atendimento_individual (
  id BIGSERIAL PRIMARY KEY, unidade VARCHAR(30) NOT NULL, microarea VARCHAR(30) NOT NULL, id_cadastro BIGINT, id_paciente INTEGER,
  nome_paciente VARCHAR(120) NOT NULL, profissional VARCHAR(120) NOT NULL, cbo VARCHAR(10), tipo_atendimento VARCHAR(40) NOT NULL,
  data_atendimento DATE NOT NULL, motivo VARCHAR(200) NOT NULL, cid10 VARCHAR(4), conduta VARCHAR(500), desfecho VARCHAR(80),
  observacao VARCHAR(500), criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_aps_atendimento_tipo CHECK (tipo_atendimento IN ('CONSULTA_AGENDADA', 'CONSULTA_NO_DIA', 'ESCUTA_INICIAL', 'URGENCIA'))
);
CREATE INDEX IF NOT EXISTS idx_aps_atendimento_unidade_data ON aps_atendimento_individual (unidade, data_atendimento);
CREATE INDEX IF NOT EXISTS idx_aps_atendimento_microarea ON aps_atendimento_individual (unidade, microarea);
