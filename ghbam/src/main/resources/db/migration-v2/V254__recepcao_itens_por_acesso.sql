-- Menu Recepção passa a obedecer aos acessos da tela de servidores, item por item.
-- 1) Itens que não tinham acesso próprio ganham um (padrão desligado).
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pdrives               BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS plistaagendamentos    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS plocalizaratendimento BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pprimeiroatendimento  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pvolumepainel         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS pvisitapacientes      BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Ninguém perde tela no dia da mudança: quem tem o Perfil Recepção (que até aqui abria esses itens sozinho)
--    fica com os acessos ligados. Depois disso, desligar na tela de servidores ou na coordenação tira o item.
UPDATE servidor2 s2
   SET aguardandoatendimento = TRUE,
       atendimentoperiodo    = TRUE,
       pacientesematendimento = TRUE,
       comoestaopaciente     = TRUE,
       pfluxopacientes       = TRUE,
       pdrives               = TRUE,
       plistaagendamentos    = TRUE,
       plocalizaratendimento = TRUE,
       pprimeiroatendimento  = TRUE,
       pvolumepainel         = TRUE,
       pvisitapacientes      = TRUE
  FROM servidor s
 WHERE s.matricula = s2.matricula
   AND s.recepcao = TRUE;
