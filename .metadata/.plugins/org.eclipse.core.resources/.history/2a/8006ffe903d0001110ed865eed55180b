-- Permissao mae do grupo Apoio Assistencial.
-- Libera o modulo inteiro (CME, hemoterapia, SND e RNDS), da mesma forma
-- que pcentrocirurgico libera o modulo do centro cirurgico.
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS papoioassistencial BOOLEAN DEFAULT FALSE;

-- Quem ja tinha acesso a qualquer item do grupo mantem o acesso ao modulo.
UPDATE servidor2
   SET papoioassistencial = TRUE
 WHERE COALESCE(pcme, FALSE)
    OR COALESCE(phemoterapia, FALSE)
    OR COALESCE(psnd, FALSE)
    OR COALESCE(prnds, FALSE);
