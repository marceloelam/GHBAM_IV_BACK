ALTER TABLE sae_enfermagem
  ADD COLUMN IF NOT EXISTS versao INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS uuid_origem VARCHAR(80),
  ADD COLUMN IF NOT EXISTS motivo_retificacao TEXT;

DROP INDEX IF EXISTS uq_sae_enfermagem_bam_data;
DROP INDEX IF EXISTS uq_sae_enfermagem_paciente_data_sem_bam;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_bam_data_versao
  ON sae_enfermagem (idbam, data_sae, versao)
  WHERE idbam IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_paciente_data_versao_sem_bam
  ON sae_enfermagem (idpaciente, data_sae, versao)
  WHERE idbam IS NULL AND idpaciente IS NOT NULL;
