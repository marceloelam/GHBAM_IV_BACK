-- Módulos contratados por unidade (chave: sigla). Sem linha = módulo habilitado,
-- então todas as unidades continuam com tudo ligado até alguém desligar algo.
CREATE TABLE IF NOT EXISTS unidade_modulo (
    sigla        VARCHAR(30)  NOT NULL,
    modulo       VARCHAR(40)  NOT NULL,
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    alterado_por VARCHAR(60),
    alterado_em  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_unidade_modulo PRIMARY KEY (sigla, modulo)
);
