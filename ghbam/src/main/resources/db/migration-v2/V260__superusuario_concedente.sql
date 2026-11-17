-- Quem pode conceder/retirar Super usuario alem do responsavel da plataforma
-- (ghbam.seguranca.superusuario.responsavel). So o responsavel designa ou cancela.
CREATE TABLE IF NOT EXISTS superusuario_concedente (
    id            BIGSERIAL PRIMARY KEY,
    matricula     VARCHAR(30)  NOT NULL,
    designado_por VARCHAR(30)  NOT NULL,
    criado_em     TIMESTAMP    NOT NULL DEFAULT now(),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    cancelado_por VARCHAR(30),
    cancelado_em  TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_superusuario_concedente_ativo
    ON superusuario_concedente (matricula) WHERE ativo;
