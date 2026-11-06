-- Telas favoritas do menu lateral, por usuario. Guarda apenas o caminho da tela; quem decide
-- o que aparece continua sendo o menu filtrado por permissao, entao favorito nao concede acesso.
CREATE TABLE IF NOT EXISTS usuario_menu_favorito (
    matricula VARCHAR(80) NOT NULL,
    caminho VARCHAR(300) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    PRIMARY KEY (matricula, caminho)
);
