-- Comissao de protocolos em fluxograma: opcao no cadastro de servidores (/servidores) que permite
-- aprovar, reprovar, revogar e editar os textos dos protocolos. So tem efeito para medico,
-- enfermagem ou equipe multidisciplinar (regra aplicada no backend, ProtocoloAprovacaoService).
ALTER TABLE servidor2
    ADD COLUMN IF NOT EXISTS pprotocoloaprovacao BOOLEAN NOT NULL DEFAULT FALSE;
