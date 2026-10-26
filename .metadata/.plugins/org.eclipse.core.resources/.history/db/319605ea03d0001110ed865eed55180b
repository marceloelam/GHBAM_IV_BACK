-- Cancelamento do sumário de óbito: o registro é preservado (rastreabilidade) e passa a imprimir com marca d'água "CANCELADO".
ALTER TABLE sumarioobito ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ATIVO';
ALTER TABLE sumarioobito ADD COLUMN IF NOT EXISTS canceladoem TIMESTAMP;
ALTER TABLE sumarioobito ADD COLUMN IF NOT EXISTS canceladopor VARCHAR(120);
ALTER TABLE sumarioobito ADD COLUMN IF NOT EXISTS motivocancelamento TEXT;
CREATE INDEX IF NOT EXISTS idx_sumarioobito_unidade_bam ON sumarioobito (unidade, bam);
