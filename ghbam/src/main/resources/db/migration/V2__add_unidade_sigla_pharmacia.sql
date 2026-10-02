BEGIN;

-- 1) Adiciona colunas de sigla
ALTER TABLE pharmacia_estoque ADD COLUMN IF NOT EXISTS unidade_sigla varchar(20);
ALTER TABLE pharmacia_lote ADD COLUMN IF NOT EXISTS unidade_sigla varchar(20);
ALTER TABLE pharmacia_categoria ADD COLUMN IF NOT EXISTS unidade_sigla varchar(20);
ALTER TABLE pharmacia_produto ADD COLUMN IF NOT EXISTS unidade_sigla varchar(20);
ALTER TABLE pharmacia_permuta ADD COLUMN IF NOT EXISTS unidade_origem_sigla varchar(20);
ALTER TABLE pharmacia_permuta ADD COLUMN IF NOT EXISTS unidade_destino_sigla varchar(20);
ALTER TABLE pharmacia_movimentacao ADD COLUMN IF NOT EXISTS unidade_origem_sigla varchar(20);
ALTER TABLE pharmacia_movimentacao ADD COLUMN IF NOT EXISTS unidade_destino_sigla varchar(20);

-- 2) Popula valores a partir da tabela pharmacia_unidade
UPDATE pharmacia_estoque e
SET unidade_sigla = u.sigla
FROM pharmacia_unidade u
WHERE e.unidade_id = u.id AND (e.unidade_sigla IS NULL OR e.unidade_sigla = '');

UPDATE pharmacia_lote l
SET unidade_sigla = u.sigla
FROM pharmacia_unidade u
WHERE l.unidade_id = u.id AND (l.unidade_sigla IS NULL OR l.unidade_sigla = '');

UPDATE pharmacia_categoria c
SET unidade_sigla = u.sigla
FROM pharmacia_unidade u
WHERE c.unidade_id = u.id AND (c.unidade_sigla IS NULL OR c.unidade_sigla = '');

UPDATE pharmacia_produto p
SET unidade_sigla = u.sigla
FROM pharmacia_unidade u
WHERE p.unidade_id = u.id AND (p.unidade_sigla IS NULL OR p.unidade_sigla = '');

UPDATE pharmacia_permuta pm
SET unidade_origem_sigla = u.sigla
FROM pharmacia_unidade u
WHERE pm.unidade_origem_id = u.id AND (pm.unidade_origem_sigla IS NULL OR pm.unidade_origem_sigla = '');

UPDATE pharmacia_permuta pm
SET unidade_destino_sigla = u.sigla
FROM pharmacia_unidade u
WHERE pm.unidade_destino_id = u.id AND (pm.unidade_destino_sigla IS NULL OR pm.unidade_destino_sigla = '');

UPDATE pharmacia_movimentacao m
SET unidade_origem_sigla = u.sigla
FROM pharmacia_unidade u
WHERE m.unidade_origem_id = u.id AND (m.unidade_origem_sigla IS NULL OR m.unidade_origem_sigla = '');

UPDATE pharmacia_movimentacao m
SET unidade_destino_sigla = u.sigla
FROM pharmacia_unidade u
WHERE m.unidade_destino_id = u.id AND (m.unidade_destino_sigla IS NULL OR m.unidade_destino_sigla = '');

COMMIT;
