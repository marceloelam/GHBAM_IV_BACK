CREATE TABLE IF NOT EXISTS documento_assistencial (
  id SERIAL PRIMARY KEY, uuid VARCHAR(80) NOT NULL UNIQUE, tipo VARCHAR(40) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'RASCUNHO', idpaciente INTEGER NOT NULL, idbam INTEGER,
  prontuario VARCHAR(40), nomepaciente VARCHAR(180), unidade VARCHAR(30), titulo VARCHAR(180) NOT NULL,
  conteudo_json TEXT NOT NULL DEFAULT '{}', criado_por VARCHAR(120), criado_por_matricula VARCHAR(40),
  concluido_por VARCHAR(120), concluido_por_matricula VARCHAR(40), criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, concluido_em TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_documento_assistencial_paciente ON documento_assistencial (idpaciente, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_documento_assistencial_bam ON documento_assistencial (idbam, criado_em DESC) WHERE idbam IS NOT NULL;
