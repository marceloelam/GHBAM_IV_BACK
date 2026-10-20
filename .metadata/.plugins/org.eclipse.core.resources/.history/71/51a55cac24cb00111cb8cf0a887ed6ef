CREATE TABLE IF NOT EXISTS vacina_pediatrica (
  id SERIAL PRIMARY KEY,
  idpaciente INTEGER NOT NULL,
  vacina VARCHAR(60) NOT NULL,
  dose VARCHAR(30) NOT NULL,
  data_aplicacao DATE,
  idade_aplicacao VARCHAR(15),
  observacao TEXT,
  medico VARCHAR(120),
  crm VARCHAR(15),
  criado_por_matricula VARCHAR(40),
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vacina_pediatrica_paciente
  ON vacina_pediatrica (idpaciente);
