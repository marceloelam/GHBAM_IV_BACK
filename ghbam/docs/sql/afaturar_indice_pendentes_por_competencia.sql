-- Rodar manualmente em cada ambiente, fora do Flyway, com psql e fora de transacao.
-- Motivo: a conexao do backend tem statement_timeout=60s e socketTimeout=120s; dentro do Flyway a criacao
-- poderia estourar esses limites e impedir o backend de subir. A consulta funciona sem o indice, so mais lenta.
-- Conferir antes o espaco em disco. Se falhar, DROP INDEX CONCURRENTLY e rodar de novo (fica INVALID).
SET statement_timeout = 0;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_afaturar_sem_competencia_mes
    ON afaturar ((CASE
                    WHEN data ~ '^\d{4}-\d{2}-\d{2}' THEN substring(data, 1, 4) || substring(data, 6, 2)
                    WHEN data ~ '^\d{2}/\d{2}/\d{4}' THEN substring(data, 7, 4) || substring(data, 4, 2)
                  END), (upper(unidade)))
    WHERE idcompetencia IS NULL AND coalesce(exportado, false) = false;
