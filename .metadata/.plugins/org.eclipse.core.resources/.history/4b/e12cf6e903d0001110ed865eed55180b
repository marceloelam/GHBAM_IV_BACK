ALTER TABLE prontuario_clinico_item
  ADD COLUMN IF NOT EXISTS idbam INTEGER;

CREATE INDEX IF NOT EXISTS idx_prontuario_clinico_item_bam
  ON prontuario_clinico_item (idbam);
