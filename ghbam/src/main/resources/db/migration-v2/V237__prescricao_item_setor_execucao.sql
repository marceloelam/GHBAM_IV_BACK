-- Setor que executa o item da prescricao estruturada na PA (MEDICACAO = SPA, MEDICACAO2 = Maternidade,
-- MEDICACAO3 = Pediatria). Internado continua no ciclo de medicacao da internacao. Vazio = SPA (legado).
ALTER TABLE prescricao_item_estruturado ADD COLUMN IF NOT EXISTS setor_execucao VARCHAR(20);
