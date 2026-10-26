ALTER TABLE evidencia_assistencial
    ADD COLUMN IF NOT EXISTS prontuario VARCHAR(40),
    ADD COLUMN IF NOT EXISTS origemmodulo VARCHAR(80),
    ADD COLUMN IF NOT EXISTS origemid VARCHAR(80),
    ADD COLUMN IF NOT EXISTS origemdescricao VARCHAR(220),
    ADD COLUMN IF NOT EXISTS dataatualizacao TIMESTAMP,
    ADD COLUMN IF NOT EXISTS usuarioatualizacao VARCHAR(80);

UPDATE evidencia_assistencial
   SET dataatualizacao = data_registro
 WHERE dataatualizacao IS NULL;

CREATE INDEX IF NOT EXISTS idx_evidencia_assistencial_prontuario
    ON evidencia_assistencial (prontuario);

CREATE INDEX IF NOT EXISTS idx_evidencia_assistencial_origem
    ON evidencia_assistencial (origemmodulo, origemid);
