-- Campos que ja vinham no tb_procedimento.txt do DATASUS e eram descartados na
-- importacao do SIGTAP. Sem eles o motor de validacao so conseguia conferir se o
-- procedimento existe no SIGTAP local; com eles passa a prever as duas glosas mais
-- comuns antes do envio: procedimento exclusivo de um sexo lancado para o outro e
-- procedimento fora da faixa etaria.
--
-- Todas nullable de proposito: a producao ja gravada e as competencias SIGTAP
-- importadas antes desta versao continuam validas, e a validacao nova so age quando
-- o campo esta preenchido (base que ainda nao reimportou o SIGTAP nao muda de
-- comportamento).
ALTER TABLE s_proced
    ADD COLUMN IF NOT EXISTS tp_sexo VARCHAR(1),
    ADD COLUMN IF NOT EXISTS vl_idade_minima INTEGER,
    ADD COLUMN IF NOT EXISTS vl_idade_maxima INTEGER,
    ADD COLUMN IF NOT EXISTS qt_maxima_execucao INTEGER,
    ADD COLUMN IF NOT EXISTS tp_complexidade VARCHAR(1),
    ADD COLUMN IF NOT EXISTS co_financiamento VARCHAR(2);
