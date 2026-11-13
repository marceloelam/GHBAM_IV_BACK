-- Itens do menu que a administração esconde de um servidor (tela de servidores). Padrão: tudo visível.
-- Em setor com coordenador, o membro da equipe segue os itens liberados pelo coordenador (setor_membro_item).
-- Remoção lógica para manter o rastro de quem escondeu e quem voltou a mostrar.
CREATE TABLE IF NOT EXISTS servidor_menu_item_oculto (
    id            BIGSERIAL PRIMARY KEY,
    matricula     VARCHAR(15)  NOT NULL,
    grupo         VARCHAR(120) NOT NULL,
    rota          VARCHAR(200) NOT NULL,
    incluido_por  VARCHAR(15)  NOT NULL,
    incluido_em   TIMESTAMP    NOT NULL DEFAULT now(),
    removido_por  VARCHAR(15),
    removido_em   TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_servidor_menu_item_oculto_ativo
    ON servidor_menu_item_oculto (matricula, grupo, rota) WHERE removido_em IS NULL;
