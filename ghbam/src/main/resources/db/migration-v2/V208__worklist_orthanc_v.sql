-- View da Modality Worklist do Orthanc interno do GHBAM (lida por
-- deploy/orthanc-worklist/GerarWorklist.ps1 -View worklist_orthanc_v).
-- Nao altera a worklist_v existente (usada por outro sistema).
-- Cria so se nao existir: nunca sobrescreve uma versao ajustada manualmente.
--
-- Diferencas para a worklist_v: uma linha por idimagens (sem fundir homonimos
-- nem exames repetidos), sem exigir pedidomedico2 e filtrando pela data do
-- EXAME (paciente internado em dias anteriores tambem aparece).
-- PatientID = paciente do BAM, o mesmo conferido em ImagemIaService.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_views
                    WHERE viewname = 'worklist_orthanc_v' AND schemaname = current_schema()) THEN
      BEGIN
        EXECUTE $view$
            CREATE VIEW worklist_orthanc_v AS
            SELECT DISTINCT ON (i.idimagens)
                   i.idimagens                                                AS accessionnumber,
                   b.paciente_idpaciente                                      AS pacienteid,
                   p.nome                                                     AS pacientenome,
                   replace(replace(p.nascimento::text, '/', ''), '-', '')     AS pacientenascimento,
                   p.sexo                                                     AS pacientesexo,
                   i.descricao                                                AS examenome,
                   i.procedimento                                             AS examecodigo,
                   to_char(i.data::date, 'YYYYMMDD')                          AS atendimentodata,
                   replace(i.entrada::text, ':', '')                          AS atendimentohora,
                   b.idbam                                                    AS atendimentoid,
                   b.unidade,
                   CASE WHEN i.local::text = 'RAIO X' THEN 'CR' ELSE 'CT' END AS modalidade
              FROM imagens i
              JOIN bam b       ON b.idbam = i.idbam
              JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
             WHERE i.data::date = current_date
             ORDER BY i.idimagens
        $view$;
      EXCEPTION WHEN others THEN
        -- Banco sem alguma coluna/tabela: a view e opcional e nao pode impedir o
        -- backend de subir. Criar depois a mao com deploy/orthanc-worklist/worklist_orthanc_v.sql.
        RAISE WARNING 'worklist_orthanc_v nao criada: %', SQLERRM;
      END;
    END IF;

    -- Leitura para o usuario do Orthanc, se ele ja tiver sido criado pelo DBA.
    IF EXISTS (SELECT 1 FROM pg_views
                WHERE viewname = 'worklist_orthanc_v' AND schemaname = current_schema())
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'orthanc_worklist') THEN
        EXECUTE 'GRANT SELECT ON worklist_orthanc_v TO orthanc_worklist';
    END IF;
END
$$;
