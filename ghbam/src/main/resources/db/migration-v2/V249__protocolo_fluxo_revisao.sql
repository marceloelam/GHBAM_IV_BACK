-- Edicao dos TEXTOS dos protocolos em fluxograma pela comissao (estrutura do fluxograma fica no
-- codigo e nao muda aqui). Cada edicao salva gera uma nova versao "<versao_base>-rN", que entra
-- como rascunho ate ser aprovada (protocolo_fluxo_aprovacao). Append-only: nada e sobrescrito.
CREATE TABLE IF NOT EXISTS protocolo_fluxo_revisao (
    id BIGSERIAL PRIMARY KEY,
    protocolo_id VARCHAR(80) NOT NULL,
    versao_base VARCHAR(20) NOT NULL,
    versao VARCHAR(30) NOT NULL,
    textos JSONB NOT NULL,
    justificativa TEXT NOT NULL,
    matricula VARCHAR(80) NOT NULL,
    nome VARCHAR(200),
    registro VARCHAR(60),
    unidade VARCHAR(80),
    criado_em TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT protocolo_fluxo_revisao_versao_uk UNIQUE (protocolo_id, versao)
);

CREATE INDEX IF NOT EXISTS protocolo_fluxo_revisao_idx
    ON protocolo_fluxo_revisao (protocolo_id, versao_base, criado_em DESC);
