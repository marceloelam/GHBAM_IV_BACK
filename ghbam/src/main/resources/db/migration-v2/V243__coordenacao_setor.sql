-- Coordenacao de setor: a administracao define quem coordena e quem compoe a equipe de cada setor
-- da unidade; o coordenador altera acessos (servidor2) apenas da propria equipe. So tabelas novas.

CREATE TABLE IF NOT EXISTS setor_coordenacao (
    id BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(30) NOT NULL,
    setor VARCHAR(120) NOT NULL,
    matricula VARCHAR(15) NOT NULL,
    papel VARCHAR(12) NOT NULL CHECK (papel IN ('COORDENADOR', 'MEMBRO')),
    incluido_por VARCHAR(15) NOT NULL,
    incluido_em TIMESTAMP NOT NULL DEFAULT now(),
    removido_por VARCHAR(15),
    removido_em TIMESTAMP
);

-- Uma pessoa tem um unico papel ativo por setor; o historico (removidos) fica preservado.
CREATE UNIQUE INDEX IF NOT EXISTS ux_setor_coordenacao_ativo
    ON setor_coordenacao (unidade, setor, matricula) WHERE removido_em IS NULL;
CREATE INDEX IF NOT EXISTS idx_setor_coordenacao_matricula
    ON setor_coordenacao (matricula) WHERE removido_em IS NULL;

-- Trilha de toda alteracao de acesso feita pela coordenacao (somente INSERT).
CREATE TABLE IF NOT EXISTS setor_permissao_auditoria (
    id BIGSERIAL PRIMARY KEY,
    unidade VARCHAR(30) NOT NULL,
    setor VARCHAR(120) NOT NULL,
    matricula_autor VARCHAR(15) NOT NULL,
    nome_autor VARCHAR(80),
    matricula_alvo VARCHAR(15) NOT NULL,
    chave VARCHAR(60) NOT NULL,
    valor_anterior BOOLEAN,
    valor_novo BOOLEAN NOT NULL,
    justificativa VARCHAR(500),
    alterado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_setor_permissao_auditoria_unidade
    ON setor_permissao_auditoria (unidade, setor, alterado_em DESC);
CREATE INDEX IF NOT EXISTS idx_setor_permissao_auditoria_alvo
    ON setor_permissao_auditoria (matricula_alvo, alterado_em DESC);
