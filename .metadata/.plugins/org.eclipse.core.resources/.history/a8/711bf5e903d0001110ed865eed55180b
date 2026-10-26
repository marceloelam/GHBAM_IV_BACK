-- O prontuario completo consulta os registros clinicos por paciente e BAM.
-- Estes indices evitam varreduras completas nas tabelas de historico.
CREATE INDEX IF NOT EXISTS idx_bam_paciente_idbam ON bam (paciente_idpaciente, idbam DESC);
CREATE INDEX IF NOT EXISTS idx_atendimento_bam_data ON atendimento (bam, data DESC, hora DESC);
CREATE INDEX IF NOT EXISTS idx_prescricao_bam_data ON prescricao (bam, data DESC, hora DESC);
CREATE INDEX IF NOT EXISTS idx_pedido_medico_bam_data ON pedidomedico2 (bam, dt DESC, hr DESC);
CREATE INDEX IF NOT EXISTS idx_resultado_laboratorio_bam_data ON pedidoresultadolaboratorio (bam, dt DESC, hr DESC);
CREATE INDEX IF NOT EXISTS idx_evolucao_paciente_nbam_data ON evolucaopaciente (nbam, data DESC, horaev DESC);
CREATE INDEX IF NOT EXISTS idx_evolucao_enf_nbam_data ON evolucaopacienteenf (nbam, data DESC, hora DESC);
CREATE INDEX IF NOT EXISTS idx_anamnese_internacao_nbam ON anamneseinternacao (nbam, idanamneseinternacao DESC);
CREATE INDEX IF NOT EXISTS idx_anotacao_enfermagem_idbam ON anotacao_enfermagem (idbam, criado_em DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_prescricao_estruturada_idbam ON prescricao_item_estruturado (idbam, criado_em DESC, iditem DESC);
CREATE INDEX IF NOT EXISTS idx_internacao_bam_datas ON internacao (bam, datainternacao, datasaida);
