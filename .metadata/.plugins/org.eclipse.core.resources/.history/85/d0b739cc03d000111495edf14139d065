-- Permissao dedicada para classificar/finalizar casos do NSP.
-- Antes, pnspgestao fazia tudo: ver o notificante e classificar/investigar/encerrar.
-- Agora pnspgestao so libera ver o notificante; esta coluna separa quem pode de
-- fato classificar, montar plano de acao e encerrar o caso.
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pnspclassificar BOOLEAN DEFAULT FALSE;
