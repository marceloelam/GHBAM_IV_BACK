ALTER TABLE sae_enfermagem ADD COLUMN IF NOT EXISTS tipo_sae VARCHAR(20) NOT NULL DEFAULT 'GERAL';
DROP INDEX IF EXISTS uq_sae_enfermagem_bam_data_versao;
DROP INDEX IF EXISTS uq_sae_enfermagem_paciente_data_versao_sem_bam;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_bam_data_tipo_versao ON sae_enfermagem (idbam, data_sae, tipo_sae, versao) WHERE idbam IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sae_enfermagem_paciente_data_tipo_versao_sem_bam ON sae_enfermagem (idpaciente, data_sae, tipo_sae, versao) WHERE idbam IS NULL AND idpaciente IS NOT NULL;
