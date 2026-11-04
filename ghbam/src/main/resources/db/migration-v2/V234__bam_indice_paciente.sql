-- "BAMs deste paciente" percorria a tabela inteira (525 mil linhas, sem indice por paciente):
-- busca do cabecalho, situacao atual, NIR, historico e unificacao de cadastros.
-- Trava gravacoes em bam por alguns segundos enquanto cria: subir o backend fora do pico.
CREATE INDEX IF NOT EXISTS idx_bam_paciente ON bam (paciente_idpaciente);
