-- Log de tutoriais: quem abriu o "Como usar" (passo a passo) ou o video de ajuda de cada tela.
-- Matricula, nome e unidade vem do token de quem assistiu, nunca do cliente.
CREATE TABLE IF NOT EXISTS tutorial_visualizacao (
    id BIGSERIAL PRIMARY KEY,
    matricula VARCHAR(80) NOT NULL,
    nome VARCHAR(200),
    unidade VARCHAR(80),
    tutorial VARCHAR(200) NOT NULL,
    tela VARCHAR(300),
    tipo VARCHAR(20) NOT NULL DEFAULT 'PASSO_A_PASSO',
    visto_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tutorial_visualizacao_unidade_data ON tutorial_visualizacao (unidade, visto_em DESC);
CREATE INDEX IF NOT EXISTS idx_tutorial_visualizacao_matricula ON tutorial_visualizacao (matricula, visto_em DESC);
