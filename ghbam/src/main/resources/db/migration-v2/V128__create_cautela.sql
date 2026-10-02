-- Cautela: entrega de um bem do hospital a uma pessoa, sob responsabilidade dela,
-- com obrigacao de devolver. Nao e consumo (o item continua sendo do hospital) nem
-- transferencia de estoque - e o rastro de "com quem esta" e "desde quando".
--
-- Cobre os quatro usos que hoje vivem em caderno/planilha: patrimonio e equipamento
-- emprestado a setor, prontuario fisico retirado do arquivo (SAME), caixa de
-- instrumental cirurgico e chave/EPI/uniforme.
--
-- O valor do modulo nao esta no cadastro, esta no que NAO voltou: por isso
-- data_prevista e situacao sao os campos indexados.
CREATE TABLE IF NOT EXISTS cautela (
    id                    SERIAL PRIMARY KEY,
    uuid                  VARCHAR(80)  NOT NULL UNIQUE,
    categoria             VARCHAR(40)  NOT NULL,
    item                  VARCHAR(255) NOT NULL,
    -- Numero de patrimonio/tombo, serie do equipamento ou numero do prontuario.
    -- Texto livre de proposito: cada categoria numera do seu jeito.
    identificador         VARCHAR(80),
    quantidade            INTEGER      NOT NULL DEFAULT 1,
    responsavel_nome      VARCHAR(180) NOT NULL,
    responsavel_matricula VARCHAR(40),
    setor                 VARCHAR(120),
    unidade               VARCHAR(30),
    finalidade            TEXT,
    observacao            TEXT,
    -- EM_CAUTELA | DEVOLVIDO | CANCELADO
    situacao              VARCHAR(20)  NOT NULL DEFAULT 'EM_CAUTELA',
    data_saida            TIMESTAMP    NOT NULL,
    data_prevista         TIMESTAMP,
    data_devolucao        TIMESTAMP,
    -- OK | AVARIADO | EXTRAVIADO - o estado importa tanto quanto a data:
    -- item que "voltou avariado" e a informacao que o patrimonio cobra.
    estado_devolucao      VARCHAR(30),
    observacao_devolucao  TEXT,
    motivo_cancelamento   TEXT,
    criado_por            VARCHAR(120),
    criado_por_matricula  VARCHAR(40),
    devolvido_por         VARCHAR(120),
    devolvido_por_matricula VARCHAR(40),
    cancelado_por         VARCHAR(120),
    cancelado_por_matricula VARCHAR(40),
    criado_em             TIMESTAMP,
    atualizado_em         TIMESTAMP
);

-- Consulta padrao da tela: pendencias da unidade onde o operador esta logado.
CREATE INDEX IF NOT EXISTS idx_cautela_unidade_situacao ON cautela (unidade, situacao);
-- "O que esta comigo / com fulano" e a segunda pergunta mais frequente.
CREATE INDEX IF NOT EXISTS idx_cautela_responsavel ON cautela (responsavel_matricula);
-- Relatorio de vencidas: situacao EM_CAUTELA com data_prevista no passado.
CREATE INDEX IF NOT EXISTS idx_cautela_prevista ON cautela (data_prevista);
