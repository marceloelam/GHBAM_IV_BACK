CREATE TABLE IF NOT EXISTS multidisciplinar_pts (
  id SERIAL PRIMARY KEY,
  idpaciente INTEGER NOT NULL,
  id_atendimento INTEGER,

  problema TEXT NOT NULL,
  objetivo TEXT,
  acao TEXT,
  profissional VARCHAR(120),
  categoria_profissional VARCHAR(40),
  matricula_responsavel VARCHAR(40),

  data_inicio DATE,
  prazo VARCHAR(60),
  status VARCHAR(20) NOT NULL DEFAULT 'PLANEJADO',
  resultado_esperado TEXT,
  resultado_alcancado TEXT,
  observacao TEXT,
  data_conclusao DATE,

  criado_por_matricula VARCHAR(40),
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_multi_pts_paciente ON multidisciplinar_pts (idpaciente);
CREATE INDEX IF NOT EXISTS idx_multi_pts_atendimento ON multidisciplinar_pts (id_atendimento);
