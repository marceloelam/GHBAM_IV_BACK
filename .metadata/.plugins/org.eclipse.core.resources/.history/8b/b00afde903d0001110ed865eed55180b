-- Geracao de remessa quebrava no insert com "valor e muito longo para tipo character
-- varying(20)": o layout do BPA oficial e 'BPA-DATASUS-FEHOSP-2020' (23 caracteres) e
-- nao cabia em layout VARCHAR(20).
--
-- O numero da remessa tinha o mesmo problema logo em seguida: ele carrega a sigla da
-- unidade ('GHBAM-202607-<UNIDADE>-00001'), e unidade com nome longo passa dos 30
-- caracteres. Ampliar os dois de uma vez evita descobrir o segundo estouro so no
-- proximo clique.
ALTER TABLE faturamento_remessa
    ALTER COLUMN layout TYPE VARCHAR(40);

ALTER TABLE faturamento_remessa
    ALTER COLUMN numero TYPE VARCHAR(60);
