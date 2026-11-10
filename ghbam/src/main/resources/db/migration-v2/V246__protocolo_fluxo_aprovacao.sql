-- Governanca dos protocolos em fluxograma: cada decisao (aprovar, reprovar, revogar) da comissao
-- vira uma linha, com quem decidiu (do login) e quando. Nada e sobrescrito: a situacao atual de
-- um protocolo/versao e a decisao mais recente; o resto e historico auditavel.
-- O conteudo do protocolo continua no frontend; aqui so a decisao sobre (protocolo_id, versao).
CREATE TABLE IF NOT EXISTS protocolo_fluxo_aprovacao (
    id BIGSERIAL PRIMARY KEY,
    protocolo_id VARCHAR(80) NOT NULL,
    protocolo_versao VARCHAR(20) NOT NULL,
    decisao VARCHAR(12) NOT NULL,
    comentario TEXT,
    matricula VARCHAR(80) NOT NULL,
    nome VARCHAR(200),
    decidido_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT protocolo_fluxo_aprovacao_decisao_ck CHECK (decisao IN ('APROVADO', 'REPROVADO', 'REVOGADO'))
);

CREATE INDEX IF NOT EXISTS protocolo_fluxo_aprovacao_idx
    ON protocolo_fluxo_aprovacao (protocolo_id, protocolo_versao, decidido_em DESC);
