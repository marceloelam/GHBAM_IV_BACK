-- Unificacao de cadastros duplicados do paciente (Correcao de BAM).
-- O cadastro duplicado nao e apagado: fica marcado com o id do cadastro que ficou,
-- e cada unificacao guarda quem fez, quando, por que e o que foi movido.
ALTER TABLE pacientes
    ADD COLUMN IF NOT EXISTS unificado_em INTEGER;

CREATE INDEX IF NOT EXISTS idx_pacientes_unificado_em ON pacientes (unificado_em) WHERE unificado_em IS NOT NULL;

CREATE TABLE IF NOT EXISTS paciente_unificacao (
    idunificacao      BIGSERIAL PRIMARY KEY,
    id_origem         INTEGER      NOT NULL,
    id_destino        INTEGER      NOT NULL,
    motivo            VARCHAR(500) NOT NULL,
    unidade           VARCHAR(40),
    matricula         VARCHAR(40)  NOT NULL,
    data_unificacao   TIMESTAMP    NOT NULL DEFAULT now(),
    registros_movidos TEXT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_paciente_unificacao_origem ON paciente_unificacao (id_origem);
CREATE INDEX IF NOT EXISTS idx_paciente_unificacao_destino ON paciente_unificacao (id_destino);
