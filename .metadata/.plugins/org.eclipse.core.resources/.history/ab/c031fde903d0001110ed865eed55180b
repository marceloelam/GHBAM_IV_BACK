-- Vinculo da linha de producao com a remessa em que ela foi exportada.
--
-- Ate aqui a producao so guardava exportado = true, sem dizer em qual remessa saiu. Com
-- isso nao havia como cancelar uma remessa: liberar a producao de volta exigiria adivinhar
-- quais linhas pertenciam a ela, e cancelar a primeira remessa do mes acabaria liberando
-- tambem a producao que saiu na segunda.
--
-- Linhas exportadas antes desta coluna ficam com numero_remessa nulo. O cancelamento trata
-- esse caso a parte: so libera por competencia/unidade/instrumento quando aquela e a unica
-- remessa viva do recorte, onde nao ha ambiguidade possivel.
ALTER TABLE afaturar
    ADD COLUMN IF NOT EXISTS numero_remessa VARCHAR(60);

CREATE INDEX IF NOT EXISTS idx_afaturar_numero_remessa
    ON afaturar (numero_remessa);
