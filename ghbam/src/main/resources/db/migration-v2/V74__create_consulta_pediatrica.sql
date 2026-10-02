CREATE TABLE IF NOT EXISTS consulta_pediatrica (
  id SERIAL PRIMARY KEY,
  idbam INTEGER,
  idpaciente INTEGER NOT NULL,
  prontuario VARCHAR(40),
  nbam INTEGER,
  data_consulta TIMESTAMP,
  idade_meses INTEGER,

  peso NUMERIC(6,3),
  estatura NUMERIC(6,2),
  perimetro_cefalico NUMERIC(5,2),
  imc NUMERIC(6,2),

  idade_gestacional_semanas INTEGER,
  tipo_parto VARCHAR(20),
  peso_nascimento NUMERIC(6,3),
  estatura_nascimento NUMERIC(5,2),
  perimetro_cefalico_nascimento NUMERIC(5,2),
  apgar1 INTEGER,
  apgar5 INTEGER,

  queixa_principal TEXT,
  hda TEXT,
  alimentacao TEXT,
  historico_vacinal TEXT,
  historico_familiar TEXT,

  dnpm_motor_grosso VARCHAR(20),
  dnpm_motor_fino VARCHAR(20),
  dnpm_linguagem VARCHAR(20),
  dnpm_pessoal_social VARCHAR(20),
  dnpm_observacao TEXT,

  hipotese_diagnostica TEXT,
  cid VARCHAR(10),
  conduta TEXT,

  medico VARCHAR(120),
  crm VARCHAR(15),
  criado_em TIMESTAMP,
  atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_consulta_pediatrica_paciente
  ON consulta_pediatrica (idpaciente);

CREATE INDEX IF NOT EXISTS idx_consulta_pediatrica_nbam
  ON consulta_pediatrica (nbam);
