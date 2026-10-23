-- Worklist do Orthanc: o exame so entra depois que o setor de RAIO X/TOMOGRAFIA
-- finaliza o atendimento (botao Finalizar + "Sim"). Com pedido medico, isso marca o
-- pedido como efetuado; enquanto houver pedido pendente (efetuado = false) do mesmo
-- local no BAM, o exame fica fora. Paciente direto da recepcao so ganha a linha em
-- imagens na propria finalizacao (PedidoMedico2Service.finalizarExameDiretoSetor).
-- Mesmas colunas da V208 (CREATE OR REPLACE exige isso). A worklist_v nao muda.

DO $$
BEGIN
    BEGIN
        EXECUTE $view$
            CREATE OR REPLACE VIEW worklist_orthanc_v AS
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
               AND NOT EXISTS (SELECT 1 FROM pedidomedico2 pm
                                WHERE pm.bam = i.idbam AND pm.local = i.local AND pm.efetuado = false)
             ORDER BY i.idimagens
        $view$;
    EXCEPTION WHEN others THEN
        -- Opcional: nao pode impedir o backend de subir.
        RAISE WARNING 'worklist_orthanc_v nao atualizada: %', SQLERRM;
    END;

    IF EXISTS (SELECT 1 FROM pg_views
                WHERE viewname = 'worklist_orthanc_v' AND schemaname = current_schema())
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'orthanc_worklist') THEN
        EXECUTE 'GRANT SELECT ON worklist_orthanc_v TO orthanc_worklist';
    END IF;
END
$$;
