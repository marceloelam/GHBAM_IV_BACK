-- Analise de imagem por IA (MONAI). Uma linha por analise solicitada sobre um
-- exame de imagem de um BAM. A IA SUGERE um achado; a conduta e a decisao sao
-- sempre do profissional, cuja ciencia fica gravada aqui (ciencia_*), na mesma
-- logica de rastreabilidade do resultado critico de laboratorio.
-- Modulo desligado por padrao (ver ghbam.monai.* em application.properties);
-- esta tabela so recebe linhas quando uma unidade habilitada dispara a analise.
CREATE TABLE IF NOT EXISTS imagem_ia_analise (
    id                       BIGSERIAL PRIMARY KEY,
    idbam                    INTEGER NOT NULL,
    paciente_id              INTEGER,
    idimagens                VARCHAR(40),
    descricao_exame          VARCHAR(240),
    modelo                   VARCHAR(120),
    versao_modelo            VARCHAR(40),
    achado                   TEXT,
    severidade               VARCHAR(20) NOT NULL DEFAULT 'INFORMATIVO',
    score                    NUMERIC(5,4),
    detalhes_json            TEXT,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    erro                     VARCHAR(240),
    unidade                  VARCHAR(80),
    solicitado_por_matricula VARCHAR(40),
    solicitado_por_nome      VARCHAR(120),
    criado_em                TIMESTAMP NOT NULL DEFAULT now(),
    ciencia_em               TIMESTAMP,
    ciencia_matricula        VARCHAR(40),
    ciencia_nome             VARCHAR(120),
    ciencia_conduta          TEXT
);

CREATE INDEX IF NOT EXISTS ix_imagem_ia_analise_bam ON imagem_ia_analise (idbam);
CREATE INDEX IF NOT EXISTS ix_imagem_ia_analise_status ON imagem_ia_analise (status);
