-- As colunas de workflow do Faturamento SUS foram criadas em V92 com DEFAULT FALSE,
-- mas o DEFAULT so vale para INSERT que omite a coluna. O Hibernate sempre envia a
-- coluna, entao toda producao gravada pelo fluxo assistencial nasceu com
-- exportado/faturado/possui_critica = NULL.
--
-- Consequencia: as consultas de lote filtram por "exportado = false" e NULL nunca
-- casa. Essas producoes nunca eram reprocessadas (processar-competencia), nunca
-- entravam na exportacao BPA e ficavam presas em PENDENTE_CLASSIFICACAO na tela de
-- criticas. As queries passaram a usar coalesce(exportado, false); esta migration
-- normaliza o dado ja gravado e fecha a porta com NOT NULL + DEFAULT.

UPDATE afaturar SET exportado = FALSE WHERE exportado IS NULL;
UPDATE afaturar SET faturado = FALSE WHERE faturado IS NULL;
UPDATE afaturar SET possui_critica = FALSE WHERE possui_critica IS NULL;
UPDATE afaturar SET classificacao_manual = FALSE WHERE classificacao_manual IS NULL;

ALTER TABLE afaturar
    ALTER COLUMN exportado SET DEFAULT FALSE,
    ALTER COLUMN exportado SET NOT NULL,
    ALTER COLUMN faturado SET DEFAULT FALSE,
    ALTER COLUMN faturado SET NOT NULL,
    ALTER COLUMN possui_critica SET DEFAULT FALSE,
    ALTER COLUMN possui_critica SET NOT NULL,
    ALTER COLUMN classificacao_manual SET DEFAULT FALSE,
    ALTER COLUMN classificacao_manual SET NOT NULL;
