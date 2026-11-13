-- Dentista e Multidisciplinar: itens que ainda apareciam sem acesso próprio passam a obedecer à tela de servidores.
ALTER TABLE servidor2 ADD COLUMN IF NOT EXISTS podontologia BOOLEAN NOT NULL DEFAULT FALSE;

-- Quem tem o perfil Dentista já via Prontuário odontológico, Histórico do paciente e Pendências de assinatura.
UPDATE servidor2 s2 SET podontologia = TRUE, historicocompleto = TRUE, minhasassinaturas = TRUE
  FROM servidor s WHERE s.matricula = s2.matricula AND s.dentista = TRUE;

-- Protocolos em fluxograma no Multidisciplinar seguia o acesso de APROVAR protocolos; agora segue o de tela.
UPDATE servidor2 SET pprotocolosfluxograma = TRUE WHERE pprotocolosfluxograma IS DISTINCT FROM TRUE AND pprotocoloaprovacao = TRUE;
