-- Faixa de numeracao da AIH.
--
-- O layout oficial do SISAIH01 exige o campo NU_AIH (seq 8, posicoes 44-56, 13 digitos).
-- Esse numero nao e do hospital: vem de uma faixa autorizada pelo gestor municipal ou
-- estadual, e o SISAIH01 bloqueia lote com numero fora da faixa ou repetido. Era o unico
-- dado que faltava para o GHBAM gerar o arquivo da AIH a partir da propria producao -
-- todos os outros 90 campos ja saem da conta hospitalar.
--
-- Ate aqui esse numero nao existia em lugar nenhum: laudoautorizacao.nainternacao, que
-- seria o lugar natural, esta preenchido com '0000' em 27.031 laudos.
create table if not exists faturamento_aih_faixa (
    idfaixa           serial primary key,
    unidade           varchar(30)  not null,
    -- Nula quando a faixa vale para qualquer competencia, que e o caso comum: o gestor
    -- entrega um bloco e ele e consumido ate acabar.
    competencia       varchar(6),
    numero_inicial    varchar(13)  not null,
    numero_final      varchar(13)  not null,
    -- Proximo numero a usar. Fica na propria faixa (e nao calculado a partir das contas)
    -- para que devolver ou pular numero seja uma correcao explicita e auditavel.
    proximo_numero    varchar(13)  not null,
    orgao_emissor     varchar(10),
    observacao        varchar(255),
    ativa             boolean      not null default true,
    criado_em         timestamp    not null default now(),
    usuario_criador   varchar(60)
);

create index if not exists idx_faturamento_aih_faixa_unidade
    on faturamento_aih_faixa (unidade, ativa);

-- O numero oficial atribuido a conta. Separado de faturamento_aih.numero, que e a
-- identificacao interna (AIH-202607-00082) e continua servindo para o operador achar a
-- conta na tela.
alter table faturamento_aih add column if not exists numero_aih varchar(13);

-- Numero repetido e rejeicao certa no SISAIH01. O indice parcial deixa conviverem as
-- contas ainda sem numero, que sao a maioria enquanto nao houver faixa cadastrada.
create unique index if not exists ux_faturamento_aih_numero_aih
    on faturamento_aih (numero_aih)
 where numero_aih is not null;
