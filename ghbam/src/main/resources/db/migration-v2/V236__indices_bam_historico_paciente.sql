-- Historico completo do paciente consulta 14 tabelas por BAM; 11 delas varriam a tabela inteira
-- (atendimento 430 mil linhas, pedidomedico2 1,1 milhao). Paciente com 655 BAMs levava minutos.
-- Trava gravacoes nessas tabelas por alguns segundos enquanto cria: subir o backend fora do pico.
CREATE INDEX IF NOT EXISTS idx_atendimento_bam ON atendimento (bam);
CREATE INDEX IF NOT EXISTS idx_pedidomedico2_bam ON pedidomedico2 (bam);
CREATE INDEX IF NOT EXISTS idx_pedidoresultadolaboratorio_bam ON pedidoresultadolaboratorio (bam);
CREATE INDEX IF NOT EXISTS idx_evolucaopaciente_nbam ON evolucaopaciente (nbam);
CREATE INDEX IF NOT EXISTS idx_anamneseinternacao_nbam ON anamneseinternacao (nbam);
CREATE INDEX IF NOT EXISTS idx_parecerespecialista_nbam ON parecerespecialista (nbam);
CREATE INDEX IF NOT EXISTS idx_internacao_bam ON internacao (bam);
CREATE INDEX IF NOT EXISTS idx_novointernado_nbam ON novointernado (nbam);
CREATE INDEX IF NOT EXISTS idx_resumodealta_bam ON resumodealta (bam);
CREATE INDEX IF NOT EXISTS idx_sumarioobito_bam ON sumarioobito (bam);
CREATE INDEX IF NOT EXISTS idx_anotacao_enfermagem_idbam ON anotacao_enfermagem (idbam);
