CREATE TABLE IF NOT EXISTS aps_agendamento (
  id BIGSERIAL PRIMARY KEY, unidade VARCHAR(30) NOT NULL, microarea VARCHAR(30) NOT NULL, id_cadastro BIGINT,
  nome_paciente VARCHAR(120) NOT NULL, profissional VARCHAR(120) NOT NULL, matricula_profissional VARCHAR(10),
  data_hora TIMESTAMP NOT NULL, duracao_minutos INTEGER NOT NULL DEFAULT 30, status VARCHAR(20) NOT NULL DEFAULT 'AGENDADO',
  motivo VARCHAR(200), observacao VARCHAR(500), id_atendimento BIGINT,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_aps_agendamento_status CHECK (status IN ('AGENDADO', 'CONFIRMADO', 'EM_ATENDIMENTO', 'ATENDIDO', 'FALTOU', 'CANCELADO'))
);
CREATE INDEX IF NOT EXISTS idx_aps_agendamento_unidade_data ON aps_agendamento (unidade, data_hora);
CREATE INDEX IF NOT EXISTS idx_aps_agendamento_profissional_data ON aps_agendamento (profissional, data_hora);
