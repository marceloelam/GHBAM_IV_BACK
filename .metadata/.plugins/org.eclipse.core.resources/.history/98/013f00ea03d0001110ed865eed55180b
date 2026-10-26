ALTER TABLE servidor
    ADD COLUMN IF NOT EXISTS celular_pareado_ativo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS celular_pareado_nome VARCHAR(120),
    ADD COLUMN IF NOT EXISTS celular_pareado_em TIMESTAMP;

UPDATE servidor s
SET celular_pareado_ativo = TRUE,
    celular_pareado_nome = d.nome_dispositivo,
    celular_pareado_em = d.criado_em
FROM (
    SELECT DISTINCT ON (matricula) matricula, nome_dispositivo, criado_em
    FROM push_dispositivo
    WHERE ativo = TRUE
    ORDER BY matricula, criado_em DESC
) d
WHERE s.matricula = d.matricula;
