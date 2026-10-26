ALTER TABLE prescricao_item_estruturado
  ADD COLUMN IF NOT EXISTS tipo_item VARCHAR(40) NOT NULL DEFAULT 'MEDICAMENTO';

UPDATE prescricao_item_estruturado
SET tipo_item = CASE
  WHEN medicamento ILIKE 'Dieta:%' THEN 'DIETA'
  WHEN medicamento ILIKE 'Cuidado:%' THEN 'CUIDADO'
  WHEN medicamento ILIKE 'Procedimento:%' THEN 'PROCEDIMENTO'
  WHEN medicamento ILIKE 'Oxigenoterapia:%' THEN 'OXIGENOTERAPIA'
  ELSE COALESCE(NULLIF(tipo_item, ''), 'MEDICAMENTO')
END;
