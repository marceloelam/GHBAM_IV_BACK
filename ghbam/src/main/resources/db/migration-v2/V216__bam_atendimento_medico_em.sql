-- Momento em que o medico abriu o paciente pela primeira vez (AtMedico / AtMedicoAgenda
-- marcam o BAM como "em atendimento"). Usado no BI da PA para Triagem -> medico e
-- Medico -> pedido. BAMs anteriores a esta migration ficam sem valor.
ALTER TABLE bam ADD COLUMN IF NOT EXISTS atendimento_medico_em TIMESTAMP;
