-- Controle de inatividade da conta (decisão 22/11/2026): 30 dias sem acesso = alerta aos líderes;
-- 60 dias = conta suspensa até um líder reativar. Super usuário e afastados (até a data) ficam de fora.
-- Nada é apagado: histórico e assinaturas continuam.
CREATE TABLE IF NOT EXISTS servidor_acesso_controle (
    idservidor      INTEGER      PRIMARY KEY,
    matricula       VARCHAR(30),
    cadastrado_em   TIMESTAMP    NOT NULL DEFAULT now(),
    afastado_ate    DATE,
    afastado_motivo TEXT,
    afastado_por    VARCHAR(30),
    suspenso_em     TIMESTAMP,
    reativado_em    TIMESTAMP,
    reativado_por   VARCHAR(30)
);

-- Carência: quem já existe conta a partir da implantação, para ninguém ser suspenso
-- só porque o registro de login (sessoes_ativas) é recente.
INSERT INTO servidor_acesso_controle (idservidor, matricula, cadastrado_em)
SELECT idservidor, matricula, now() FROM servidor
ON CONFLICT (idservidor) DO NOTHING;
