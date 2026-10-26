-- Nem todo mundo que leva um item do hospital e servidor: tecnico de manutencao
-- de fornecedor, representante de OPME e acompanhante tambem recebem cautela.
--
-- Sem esta coluna a unica saida seria reabrir o nome para digitacao livre, e ai
-- volta o problema que o select veio resolver (a mesma pessoa vira tres nomes e
-- some do relatorio de pendencias). Com a marca explicita da para exigir a
-- selecao no cadastro quando e servidor, e exigir documento/empresa quando nao e.
--
-- responsavel_matricula guarda a matricula quando interno e o documento ou a
-- empresa quando externo - quem diz qual dos dois e esta coluna.
ALTER TABLE cautela
    ADD COLUMN IF NOT EXISTS responsavel_externo BOOLEAN NOT NULL DEFAULT FALSE;
