-- Bloqueio individual do NSP.
-- O acesso ao Nucleo de Seguranca do Paciente vem do perfil assistencial
-- (kmedico, kenfermagem, ktriagem...), entao desmarcar pnsp nao tira o acesso de
-- quem ja e da assistencia. Esta coluna e a excecao explicita: quando true, o
-- servidor perde o acesso mesmo tendo perfil que liberaria.
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pnspbloqueado BOOLEAN DEFAULT FALSE;
