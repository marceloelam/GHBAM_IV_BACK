CREATE INDEX IF NOT EXISTS idx_documento_assistencial_paciente
    ON documento_assistencial (idpaciente, criado_em DESC);

CREATE INDEX IF NOT EXISTS idx_documento_assistencial_bam
    ON documento_assistencial (idbam, criado_em DESC)
    WHERE idbam IS NOT NULL;
