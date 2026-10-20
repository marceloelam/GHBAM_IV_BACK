-- Registro de pedidos de exclusao/eliminacao de dados (LGPD art. 18, VI).
-- O pedido e sempre negado automaticamente enquanto durar a obrigacao legal
-- de guarda do prontuario (Resolucao CFM 1.821/2007 - guarda permanente para
-- prontuario eletronico; LGPD art. 16, II autoriza a retencao nesse caso).
-- Fica registrado quem atendeu o pedido (matricula/nome do servidor) para
-- efeito de auditoria, alem da base legal informada ao solicitante.
CREATE TABLE IF NOT EXISTS lgpd_solicitacao_exclusao (
    idsolicitacao BIGSERIAL PRIMARY KEY,
    idpaciente INTEGER NOT NULL,
    nome_solicitante_externo VARCHAR(150),
    detalhe_pedido TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'NEGADA',
    base_legal TEXT NOT NULL,
    matricula_atendente VARCHAR(20) NOT NULL,
    nome_atendente VARCHAR(150),
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lgpd_solicitacao_exclusao_paciente
    ON lgpd_solicitacao_exclusao (idpaciente);
