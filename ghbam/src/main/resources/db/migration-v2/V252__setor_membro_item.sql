-- Itens do menu que o coordenador libera para cada membro da equipe do setor (grupo do menu).
-- Membro de setor com coordenação só vê, naquele grupo, os itens listados aqui (entra em branco).
-- Remoção lógica para manter o rastro; a trilha de cada mudança fica em setor_permissao_auditoria (chave "item:<rota>").
CREATE TABLE IF NOT EXISTS setor_membro_item (
    id            BIGSERIAL PRIMARY KEY,
    unidade       VARCHAR(30)  NOT NULL,
    setor         VARCHAR(120) NOT NULL,
    matricula     VARCHAR(15)  NOT NULL,
    rota          VARCHAR(200) NOT NULL,
    incluido_por  VARCHAR(15)  NOT NULL,
    incluido_em   TIMESTAMP    NOT NULL DEFAULT now(),
    removido_por  VARCHAR(15),
    removido_em   TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_setor_membro_item_ativo
    ON setor_membro_item (unidade, setor, matricula, rota) WHERE removido_em IS NULL;

CREATE INDEX IF NOT EXISTS ix_setor_membro_item_membro
    ON setor_membro_item (unidade, matricula) WHERE removido_em IS NULL;
