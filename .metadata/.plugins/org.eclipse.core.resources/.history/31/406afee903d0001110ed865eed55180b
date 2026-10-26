-- Centro Cirurgico: mapa cirurgico, execucao, seguranca e documentacao

CREATE TABLE IF NOT EXISTS cirurgia_sala (
  idsala SERIAL PRIMARY KEY,
  nome VARCHAR(60) NOT NULL,
  unidade VARCHAR(25) NOT NULL,
  cnes VARCHAR(15),
  especialidades VARCHAR(200),
  hora_inicio_expediente VARCHAR(5) DEFAULT '07:00',
  hora_fim_expediente VARCHAR(5) DEFAULT '19:00',
  tempo_limpeza_min INTEGER NOT NULL DEFAULT 30,
  ativa BOOLEAN NOT NULL DEFAULT TRUE,
  observacao VARCHAR(200),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario_criador VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_sala_unidade ON cirurgia_sala (unidade);

CREATE TABLE IF NOT EXISTS cirurgia_agendamento (
  idcirurgia SERIAL PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  idpaciente INTEGER NOT NULL,
  nomepaciente VARCHAR(80),
  cnspaciente VARCHAR(19),
  idbam INTEGER,
  idinternacao INTEGER,
  unidade VARCHAR(25) NOT NULL,
  cnes VARCHAR(15),
  idsala INTEGER,
  nomesala VARCHAR(60),
  data_prevista TIMESTAMP NOT NULL,
  duracao_prevista_min INTEGER NOT NULL DEFAULT 60,
  carater VARCHAR(20) NOT NULL DEFAULT 'ELETIVA',
  porte VARCHAR(10),
  especialidade VARCHAR(50),
  idcirurgiao INTEGER,
  nomecirurgiao VARCHAR(80),
  tipo_anestesia VARCHAR(30),
  lateralidade VARCHAR(20),
  cid VARCHAR(10),
  diagnostico VARCHAR(200),
  status VARCHAR(30) NOT NULL DEFAULT 'AGENDADA',
  necessita_uti BOOLEAN NOT NULL DEFAULT FALSE,
  necessita_hemocomponente BOOLEAN NOT NULL DEFAULT FALSE,
  reserva_sangue VARCHAR(100),
  observacao TEXT,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_criador VARCHAR(40),
  usuario_atualizacao VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_agend_data ON cirurgia_agendamento (data_prevista);
CREATE INDEX IF NOT EXISTS idx_cirurgia_agend_unidade ON cirurgia_agendamento (unidade);
CREATE INDEX IF NOT EXISTS idx_cirurgia_agend_status ON cirurgia_agendamento (status);
CREATE INDEX IF NOT EXISTS idx_cirurgia_agend_sala ON cirurgia_agendamento (idsala, data_prevista);
CREATE INDEX IF NOT EXISTS idx_cirurgia_agend_paciente ON cirurgia_agendamento (idpaciente);

CREATE TABLE IF NOT EXISTS cirurgia_procedimento (
  idprocedimento SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL,
  codigo VARCHAR(15),
  descricao VARCHAR(200) NOT NULL,
  tabela VARCHAR(10) NOT NULL DEFAULT 'SIGTAP',
  principal BOOLEAN NOT NULL DEFAULT FALSE,
  quantidade INTEGER NOT NULL DEFAULT 1,
  lateralidade VARCHAR(20),
  realizado BOOLEAN NOT NULL DEFAULT FALSE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_proc_cirurgia ON cirurgia_procedimento (idcirurgia);

CREATE TABLE IF NOT EXISTS cirurgia_equipe (
  idequipe SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL,
  idservidor INTEGER,
  matricula VARCHAR(15),
  nome VARCHAR(80) NOT NULL,
  funcao VARCHAR(30) NOT NULL,
  conselho VARCHAR(20),
  cbo VARCHAR(10),
  presente BOOLEAN NOT NULL DEFAULT FALSE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario_criador VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_equipe_cirurgia ON cirurgia_equipe (idcirurgia);
CREATE INDEX IF NOT EXISTS idx_cirurgia_equipe_servidor ON cirurgia_equipe (idservidor);

CREATE TABLE IF NOT EXISTS cirurgia_tempo (
  idtempo SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL UNIQUE,
  entrada_sala TIMESTAMP,
  inicio_anestesia TIMESTAMP,
  fim_anestesia TIMESTAMP,
  inicio_cirurgia TIMESTAMP,
  fim_cirurgia TIMESTAMP,
  saida_sala TIMESTAMP,
  inicio_limpeza TIMESTAMP,
  fim_limpeza TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_registro VARCHAR(40)
);

CREATE TABLE IF NOT EXISTS cirurgia_opme (
  idopme SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL,
  codigo VARCHAR(20),
  descricao VARCHAR(200) NOT NULL,
  fabricante VARCHAR(80),
  fornecedor VARCHAR(80),
  lote VARCHAR(40),
  registro_anvisa VARCHAR(30),
  validade VARCHAR(10),
  quantidade INTEGER NOT NULL DEFAULT 1,
  valor_unitario NUMERIC(12,2),
  utilizado BOOLEAN NOT NULL DEFAULT FALSE,
  devolvido BOOLEAN NOT NULL DEFAULT FALSE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario_registro VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_opme_cirurgia ON cirurgia_opme (idcirurgia);

CREATE TABLE IF NOT EXISTS cirurgia_contagem (
  idcontagem SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL,
  tipo VARCHAR(30) NOT NULL,
  momento VARCHAR(20) NOT NULL,
  quantidade INTEGER NOT NULL DEFAULT 0,
  conforme BOOLEAN NOT NULL DEFAULT TRUE,
  divergencia VARCHAR(300),
  conferente VARCHAR(80),
  segundo_conferente VARCHAR(80),
  registrado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario_registro VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_contagem_cirurgia ON cirurgia_contagem (idcirurgia);

CREATE TABLE IF NOT EXISTS cirurgia_descricao (
  iddescricao SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL UNIQUE,
  achados TEXT,
  descricao TEXT,
  intercorrencias TEXT,
  sangramento_ml INTEGER,
  hemocomponentes VARCHAR(200),
  material_anatomia_patologica BOOLEAN NOT NULL DEFAULT FALSE,
  material_anatomia_descricao VARCHAR(200),
  drenos VARCHAR(200),
  condicao_saida VARCHAR(50),
  destino_pos VARCHAR(30),
  assinado BOOLEAN NOT NULL DEFAULT FALSE,
  assinado_em TIMESTAMP,
  assinado_por VARCHAR(80),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_registro VARCHAR(40)
);

CREATE TABLE IF NOT EXISTS cirurgia_ficha_anestesica (
  idficha SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL UNIQUE,
  idanestesista INTEGER,
  nomeanestesista VARCHAR(80),
  tecnica VARCHAR(50),
  asa VARCHAR(5),
  mallampati VARCHAR(5),
  jejum_horas INTEGER,
  via_aerea VARCHAR(60),
  acesso_venoso VARCHAR(100),
  monitorizacao VARCHAR(200),
  drogas_json TEXT,
  sinais_json TEXT,
  intercorrencias TEXT,
  aldrete INTEGER,
  evolucao_rpa TEXT,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_registro VARCHAR(40)
);

CREATE TABLE IF NOT EXISTS cirurgia_checklist (
  idchecklist SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL,
  momento VARCHAR(20) NOT NULL,
  respostas_json TEXT,
  conforme BOOLEAN NOT NULL DEFAULT FALSE,
  pendencias VARCHAR(300),
  responsavel VARCHAR(80),
  registrado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  usuario_registro VARCHAR(40),
  CONSTRAINT uk_cirurgia_checklist_momento UNIQUE (idcirurgia, momento)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_checklist_cirurgia ON cirurgia_checklist (idcirurgia);

CREATE TABLE IF NOT EXISTS cirurgia_lista_espera (
  idespera SERIAL PRIMARY KEY,
  idpaciente INTEGER NOT NULL,
  nomepaciente VARCHAR(80),
  cnspaciente VARCHAR(19),
  unidade VARCHAR(25) NOT NULL,
  especialidade VARCHAR(50),
  procedimento_codigo VARCHAR(15),
  procedimento_descricao VARCHAR(200) NOT NULL,
  prioridade VARCHAR(20) NOT NULL DEFAULT 'ELETIVA',
  justificativa_prioridade VARCHAR(300),
  data_inclusao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  data_prevista TIMESTAMP,
  idcirurgia INTEGER,
  status VARCHAR(20) NOT NULL DEFAULT 'AGUARDANDO',
  observacao VARCHAR(300),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP,
  usuario_criador VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_espera_unidade ON cirurgia_lista_espera (unidade, status);
CREATE INDEX IF NOT EXISTS idx_cirurgia_espera_paciente ON cirurgia_lista_espera (idpaciente);

CREATE TABLE IF NOT EXISTS cirurgia_suspensao (
  idsuspensao SERIAL PRIMARY KEY,
  idcirurgia INTEGER NOT NULL,
  categoria VARCHAR(40) NOT NULL,
  motivo VARCHAR(300) NOT NULL,
  suspensa_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responsavel VARCHAR(80),
  remarcada BOOLEAN NOT NULL DEFAULT FALSE,
  idcirurgia_remarcada INTEGER,
  usuario_registro VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_cirurgia_suspensao_cirurgia ON cirurgia_suspensao (idcirurgia);
