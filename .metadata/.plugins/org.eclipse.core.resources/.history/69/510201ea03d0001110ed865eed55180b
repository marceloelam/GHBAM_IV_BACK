-- Validacao farmaceutica da dispensacao.
--
-- Duas mudancas:
-- 1) pharmacia_produto ganha os campos clinicos sem os quais nenhuma checagem
--    funciona: sem principio ativo o sistema so conhece o nome comercial e nao
--    consegue casar alergia, interacao nem duplicidade.
-- 2) pharmacia_validacao guarda o que o farmaceutico decidiu diante dos
--    alertas. A analise da prescricao e atividade privativa do farmaceutico e
--    precisa ficar documentada; alerta critico confirmado exige justificativa
--    rastreavel.

ALTER TABLE pharmacia_produto
  ADD COLUMN IF NOT EXISTS principio_ativo VARCHAR(200),
  ADD COLUMN IF NOT EXISTS concentracao VARCHAR(60),
  ADD COLUMN IF NOT EXISTS via_administracao VARCHAR(60),
  ADD COLUMN IF NOT EXISTS codigo_atc VARCHAR(20),
  ADD COLUMN IF NOT EXISTS alta_vigilancia BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS antimicrobiano BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS lista_controlada VARCHAR(10),
  ADD COLUMN IF NOT EXISTS exige_receita BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN pharmacia_produto.principio_ativo IS 'Denominacao comum brasileira. Habilita as checagens de alergia, interacao e duplicidade.';
COMMENT ON COLUMN pharmacia_produto.alta_vigilancia IS 'Medicamento potencialmente perigoso (ISMP): exige dupla checagem na dispensacao.';
COMMENT ON COLUMN pharmacia_produto.lista_controlada IS 'Lista da Portaria SVS/MS 344/1998: A1, A2, A3, B1, B2, C1, C2, C3, C4, C5.';

CREATE INDEX IF NOT EXISTS idx_pharmacia_produto_principio_ativo
  ON pharmacia_produto (principio_ativo);

CREATE TABLE IF NOT EXISTS pharmacia_validacao (
    id BIGSERIAL PRIMARY KEY,
    idpaciente INTEGER,
    paciente_nome VARCHAR(200),
    idbam INTEGER,
    unidade_sigla VARCHAR(20),
    data_hora TIMESTAMP NOT NULL,
    usuario_matricula VARCHAR(30),
    usuario_nome VARCHAR(150),
    versao_base VARCHAR(30),
    total_alertas INTEGER NOT NULL DEFAULT 0,
    alertas_criticos INTEGER NOT NULL DEFAULT 0,
    alertas_json TEXT,
    itens_json TEXT,
    justificativa VARCHAR(2000),
    dupla_checagem_matricula VARCHAR(30),
    desfecho VARCHAR(40),
    intervencao VARCHAR(1000)
);

COMMENT ON TABLE pharmacia_validacao IS 'Registro da analise farmaceutica de cada dispensacao: alertas gerados, decisao do farmaceutico e justificativa.';
COMMENT ON COLUMN pharmacia_validacao.desfecho IS 'DISPENSADO, DISPENSADO_COM_JUSTIFICATIVA, SUSPENSO, ALTERADO, APENAS_CONSULTA.';
COMMENT ON COLUMN pharmacia_validacao.versao_base IS 'Versao da base de conhecimento farmaceutico vigente na avaliacao.';

CREATE INDEX IF NOT EXISTS idx_pharmacia_validacao_paciente
  ON pharmacia_validacao (idpaciente, data_hora DESC);

CREATE INDEX IF NOT EXISTS idx_pharmacia_validacao_unidade_data
  ON pharmacia_validacao (unidade_sigla, data_hora DESC);

CREATE INDEX IF NOT EXISTS idx_pharmacia_validacao_criticos
  ON pharmacia_validacao (data_hora DESC, alertas_criticos DESC);
