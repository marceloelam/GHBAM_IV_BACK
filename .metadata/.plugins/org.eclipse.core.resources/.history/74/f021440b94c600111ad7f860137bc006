CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_bam_data
  ON sae_enfermagem (idbam, data_sae)
  WHERE idbam IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_paciente_data_sem_bam
  ON sae_enfermagem (idpaciente, data_sae)
  WHERE idbam IS NULL AND idpaciente IS NOT NULL;
