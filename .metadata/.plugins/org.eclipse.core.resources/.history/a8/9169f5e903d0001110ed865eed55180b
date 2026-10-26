CREATE TABLE IF NOT EXISTS vigilancia_epidemiologica_regra (
 id SERIAL PRIMARY KEY, unidade VARCHAR(30) NOT NULL DEFAULT '', nome VARCHAR(120) NOT NULL, termos TEXT NOT NULL,
 ativo BOOLEAN NOT NULL DEFAULT TRUE, janela_dias INTEGER NOT NULL DEFAULT 7, minimo_casos INTEGER NOT NULL DEFAULT 3,
 fator_alerta NUMERIC(8,2) NOT NULL DEFAULT 2, responsavel VARCHAR(120) NOT NULL DEFAULT '', criado_em TIMESTAMP NOT NULL DEFAULT now(), atualizado_em TIMESTAMP NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS idx_vigilancia_regra_unidade ON vigilancia_epidemiologica_regra (unidade, ativo);
