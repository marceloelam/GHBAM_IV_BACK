-- REFERENCIA. A view e criada pelo backend na migration
-- src/main/resources/db/migration-v2/V208__worklist_orthanc_v.sql (so se nao existir).
-- Este arquivo fica para as consultas de validacao do final e para recriar a mao.
-- View propria da worklist do Orthanc do GHBAM.
-- A worklist_v existente fica intocada (pode ser usada pelo PACS do fornecedor).
-- Testar primeiro na homologacao (10.200.200.7); producao so com autorizacao.
-- Uso: .\GerarWorklist.ps1 -View worklist_orthanc_v
--
-- Diferencas para a worklist_v:
--  1. Sem DISTINCT ON (i.descricao, p.nome): la, dois exames iguais do mesmo paciente
--     no dia viram um so (um AccessionNumber some) e HOMONIMOS com o mesmo exame se
--     fundem (pode sair o idimagens de um paciente com o nome de outro).
--  2. Sem JOIN em pedidomedico2: la, exame sem pedido nao aparece e varios pedidos
--     no BAM duplicam linhas (o DISTINCT ON escondia isso).
--  3. Filtra pela data do EXAME, nao do BAM: la, paciente internado ontem com RX hoje
--     nao aparece na worklist.
--  4. Uma linha por idimagens (chave do AccessionNumber).

CREATE OR REPLACE VIEW worklist_orthanc_v AS
SELECT DISTINCT ON (i.idimagens)
       i.idimagens                                                     AS accessionnumber,
       b.paciente_idpaciente                                           AS pacienteid,
       p.nome                                                          AS pacientenome,
       replace(replace(p.nascimento::text, '/', ''), '-', '')          AS pacientenascimento,
       p.sexo                                                          AS pacientesexo,
       i.descricao                                                     AS examenome,
       i.procedimento                                                  AS examecodigo,
       to_char(i.data::date, 'YYYYMMDD')                               AS atendimentodata,
       replace(i.entrada::text, ':', '')                               AS atendimentohora,
       b.idbam                                                         AS atendimentoid,
       b.unidade,
       CASE WHEN i.local::text = 'RAIO X' THEN 'CR' ELSE 'CT' END      AS modalidade
  FROM imagens i
  JOIN bam b       ON b.idbam = i.idbam
  JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
 WHERE i.data::date = current_date
   -- so depois que o setor finaliza (V209): sem pedido pendente do mesmo local no BAM
   AND NOT EXISTS (SELECT 1 FROM pedidomedico2 pm
                    WHERE pm.bam = i.idbam AND pm.local = i.local AND pm.efetuado = false)
 ORDER BY i.idimagens;

-- Atencao ao validar:
--  * i.data parece ser texto: se houver valor fora do padrao, o cast ::date no WHERE
--    derruba a consulta inteira. Conferir antes:
--      SELECT count(*) FROM imagens WHERE data !~ '^\d{4}-\d{2}-\d{2}';
--  * Modalidade: tudo que nao e 'RAIO X' vira CT (USG/RM tambem). Conferir os valores:
--      SELECT local, count(*) FROM imagens GROUP BY local ORDER BY 2 DESC;
--
-- Permissao do usuario do Orthanc:
--   GRANT SELECT ON worklist_orthanc_v TO orthanc_worklist;
