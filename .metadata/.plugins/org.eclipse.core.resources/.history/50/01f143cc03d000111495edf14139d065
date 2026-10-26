CREATE TABLE IF NOT EXISTS multidisciplinar_encaminhamento (
  id SERIAL PRIMARY KEY,
  idpaciente INTEGER NOT NULL,
  id_atendimento_origem INTEGER,

  profissional_solicitante VARCHAR(120),
  matricula_solicitante VARCHAR(40),
  categoria_origem VARCHAR(40),
  categoria_destino VARCHAR(40) NOT NULL,
  profissional_destino VARCHAR(120),

  unidade VARCHAR(20),
  setor VARCHAR(60),
  motivo TEXT,
  prioridade VARCHAR(15),
  observacao TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO',

  data_solicitacao TIMESTAMP,
  criado_por_matricula VARCHAR(40),
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_multi_enc_paciente ON multidisciplinar_encaminhamento (idpaciente);
CREATE INDEX IF NOT EXISTS idx_multi_enc_status ON multidisciplinar_encaminhamento (status);
CREATE INDEX IF NOT EXISTS idx_multi_enc_categoria_destino ON multidisciplinar_encaminhamento (categoria_destino);
