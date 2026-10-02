package br.com.iv.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("historicoPaciente")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class HistoricoPacienteController {

    @Inject
    EntityManager entityManager;

    @GET
    @Path("/buscar/{nome}/unidade/{unidade}")
    public Response buscarPorNome(@PathParam("nome") String nome, @PathParam("unidade") String unidade) {
        List<Map<String, Object>> pacientes = buscarPacientes(nome, unidade);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pacientes", pacientes);
        if (pacientes.isEmpty()) {
            response.put("historico", null);
            response.put("message", "Nenhum paciente localizado.");
            return Response.ok(response).build();
        }

        Number idpaciente = (Number) pacientes.get(0).get("idpaciente");
        response.put("historico", montarHistorico(idpaciente.intValue(), unidade));
        return Response.ok(response).build();
    }

    @GET
    @Path("/idpaciente/{idpaciente}/unidade/{unidade}")
    public Response buscarPorId(@PathParam("idpaciente") int idpaciente, @PathParam("unidade") String unidade) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pacientes", buscarPacientesPorId(idpaciente, unidade));
        response.put("historico", montarHistorico(idpaciente, unidade));
        return Response.ok(response).build();
    }

    @GET
    @Path("/bam/{bam}/unidade/{unidade}")
    public Response buscarPorBam(@PathParam("bam") int bam, @PathParam("unidade") String unidade) {
        String sql = """
                SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns,
                       MAX(b.idbam) AS ultimo_bam, MAX(CAST(b.data AS date)) AS ultima_data
                FROM pacientes p
                JOIN bam b ON b.paciente_idpaciente = p.idpaciente
                WHERE b.idbam = :bam
                GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns
                LIMIT 1
                """;
        List<Map<String, Object>> pacientes = rows(
                entityManager.createNativeQuery(sql).setParameter("bam", bam),
                "idpaciente", "nome", "idade", "prontuario", "mae", "cns", "ultimoBam", "ultimaData");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pacientes", pacientes);
        if (pacientes.isEmpty()) {
            response.put("historico", null);
            response.put("message", "Nenhum paciente localizado para o BAM informado.");
            return Response.ok(response).build();
        }
        Number idpaciente = (Number) pacientes.get(0).get("idpaciente");
        response.put("historico", montarHistorico(idpaciente.intValue(), unidade));
        return Response.ok(response).build();
    }

    @GET
    @Path("/prontuario/{prontuario}/unidade/{unidade}")
    public Response buscarPorProntuario(@PathParam("prontuario") String prontuario, @PathParam("unidade") String unidade) {
        String sql = """
                SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns,
                       MAX(b.idbam) AS ultimo_bam, MAX(CAST(b.data AS date)) AS ultima_data
                FROM pacientes p
                LEFT JOIN bam b ON b.paciente_idpaciente = p.idpaciente
                WHERE TRIM(UPPER(p.prontuario)) = TRIM(UPPER(:prontuario))
                GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns
                ORDER BY ultimo_bam DESC
                LIMIT 1
                """;
        List<Map<String, Object>> pacientes = rows(
                entityManager.createNativeQuery(sql).setParameter("prontuario", prontuario),
                "idpaciente", "nome", "idade", "prontuario", "mae", "cns", "ultimoBam", "ultimaData");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pacientes", pacientes);
        if (pacientes.isEmpty()) {
            response.put("historico", null);
            response.put("message", "Nenhum paciente localizado para o prontuário informado.");
            return Response.ok(response).build();
        }
        Number idpaciente = (Number) pacientes.get(0).get("idpaciente");
        response.put("historico", montarHistorico(idpaciente.intValue(), unidade));
        return Response.ok(response).build();
    }

    private Map<String, Object> montarHistorico(int idpaciente, String unidade) {
        Map<String, Object> historico = new LinkedHashMap<>();
        List<Map<String, Object>> bams = listarBams(idpaciente, unidade);
        Map<String, Object> paciente = buscarPaciente(idpaciente);
        Map<String, Object> ultimoBam = bams.isEmpty() ? new LinkedHashMap<>() : bams.get(0);
        List<Map<String, Object>> eventos = new ArrayList<>();

        for (Map<String, Object> bam : bams) {
            int idbam = ((Number) bam.get("idbam")).intValue();
            eventos.addAll(listarAtendimentos(idbam));
            eventos.addAll(listarPrescricoes(idbam));
            eventos.addAll(listarPrescricoesEstruturadas(idbam));
            eventos.addAll(listarPedidos(idbam));
            eventos.addAll(listarResultadosLaboratorio(idbam));
            eventos.addAll(listarEvolucoesMedicas(idbam));
            eventos.addAll(listarEvolucoesEnfermagem(idbam));
            eventos.addAll(listarAnamneseInternacao(idbam));
            eventos.addAll(listarInternacoes(idbam));
            eventos.addAll(listarNir(idbam));
            eventos.addAll(listarResumoAlta(idbam));
            eventos.addAll(listarAnotacoesEnfermagem(idbam));
        }

        // Cirurgia e fila cirurgica sao vinculadas ao paciente, nao ao BAM: entram
        // fora do laco. Sem isto o paciente podia ter sido operado, ou ter tido a
        // cirurgia cancelada com motivo registrado, e o historico completo nao
        // mostrava nada disso.
        eventos.addAll(listarCirurgias(idpaciente));
        eventos.addAll(listarListaEsperaCirurgica(idpaciente));
        eventos.addAll(listarDemandasAssistenciaisCirurgicas(idpaciente));
        eventos.addAll(listarPlanoCuidadosGhbam(idpaciente));

        // Obito pode ser registrado em varios pontos (resumo de alta, sumario de
        // obito, saida do NIR, internacao). Sem este evento o historico completo
        // nao informava que o paciente faleceu.
        List<Map<String, Object>> obitos = new ArrayList<>();
        for (Map<String, Object> bam : bams) {
            obitos.addAll(listarObitos(((Number) bam.get("idbam")).intValue()));
        }
        eventos.addAll(obitos);

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("totalBams", bams.size());
        resumo.put("totalAtendimentos", contarTipo(eventos, "ATENDIMENTO"));
        resumo.put("totalPedidos", contarTipos(eventos, List.of("PEDIDO", "RESULTADO_LAB")));
        resumo.put("totalPrescricoes", contarTipos(eventos, List.of("PRESCRICAO", "PRESCRICAO_ESTRUTURADA")));
        resumo.put("totalInternacoes", contarTipos(eventos, List.of("INTERNACAO", "NIR", "RESUMO_ALTA")));
        resumo.put("totalCirurgias", contarTipos(eventos, List.of("CIRURGIA", "LISTA_ESPERA_CIRURGICA")));
        resumo.put("totalPlanoCuidadosGhbam", contarTipo(eventos, "PLANO_GHBAM"));
        resumo.put("obito", obitos.isEmpty() ? null : obitos.get(0));

        historico.put("paciente", paciente);
        historico.put("ultimoBam", ultimoBam);
        historico.put("bams", bams);
        historico.put("eventos", eventos);
        historico.put("resumo", resumo);
        return historico;
    }

    /**
     * Eventos do plano GHBAM vinculados diretamente ao paciente e ao BAM.
     * A tabela de auditoria do catalogo identifica o item, mas o plano possui
     * os dados assistenciais necessarios para a linha do tempo do paciente.
     */
    private List<Map<String, Object>> listarPlanoCuidadosGhbam(int idpaciente) {
        String sql = """
                SELECT p.uuid::text AS evento_id, p.bam, p.incluido_em AS ocorrido_em,
                       p.incluido_por AS profissional, p.unidade,
                       'PLANO_GHBAM' AS tipo_evento,
                       'Plano GHBAM incluído' AS titulo,
                       CONCAT('Cuidado ', p.item_codigo, ' v', p.item_versao, ': ', p.item_titulo,
                              CASE WHEN NULLIF(TRIM(p.observacao), '') IS NULL THEN '' ELSE ' | Observação: ' || p.observacao END) AS detalhe,
                       p.status AS situacao
                FROM classificacao_ghbam_plano_item p
                WHERE p.idpaciente = :idpaciente

                UNION ALL

                SELECT e.id::text AS evento_id, e.bam, e.executado_em AS ocorrido_em,
                       e.executado_por AS profissional, p.unidade,
                       'PLANO_GHBAM' AS tipo_evento,
                       CASE WHEN e.situacao = 'REALIZADO' THEN 'Cuidado GHBAM realizado'
                            ELSE 'Cuidado GHBAM não realizado' END AS titulo,
                       CONCAT(p.item_codigo, ' — ', p.item_titulo,
                              ' | Situação: ', e.situacao,
                              CASE WHEN NULLIF(TRIM(e.observacao), '') IS NULL THEN '' ELSE ' | Observação: ' || e.observacao END) AS detalhe,
                       e.situacao AS situacao
                FROM classificacao_ghbam_plano_execucao e
                JOIN classificacao_ghbam_plano_item p ON p.uuid = e.plano_item_uuid
                WHERE p.idpaciente = :idpaciente

                UNION ALL

                SELECT (p.uuid || '-avaliacao')::text AS evento_id, p.bam, p.avaliado_em AS ocorrido_em,
                       p.avaliado_por AS profissional, p.unidade,
                       'PLANO_GHBAM' AS tipo_evento,
                       'Resultado GHBAM avaliado' AS titulo,
                       CONCAT(p.item_codigo, ' — ', p.item_titulo,
                              ' | Avaliação: ', p.avaliacao,
                              CASE WHEN NULLIF(TRIM(p.avaliacao_observacao), '') IS NULL THEN '' ELSE ' | Observação: ' || p.avaliacao_observacao END) AS detalhe,
                       p.avaliacao AS situacao
                FROM classificacao_ghbam_plano_item p
                WHERE p.idpaciente = :idpaciente AND p.avaliado_em IS NOT NULL

                UNION ALL

                SELECT (p.uuid || '-encerramento')::text AS evento_id, p.bam, p.encerrado_em AS ocorrido_em,
                       p.encerrado_por AS profissional, p.unidade,
                       'PLANO_GHBAM' AS tipo_evento,
                       'Cuidado GHBAM encerrado' AS titulo,
                       CONCAT(p.item_codigo, ' — ', p.item_titulo,
                              CASE WHEN NULLIF(TRIM(p.motivo_encerramento), '') IS NULL THEN '' ELSE ' | Motivo: ' || p.motivo_encerramento END) AS detalhe,
                       p.status AS situacao
                FROM classificacao_ghbam_plano_item p
                WHERE p.idpaciente = :idpaciente AND p.encerrado_em IS NOT NULL

                ORDER BY ocorrido_em DESC
                """;
        try {
            List<Object[]> registros = entityManager.createNativeQuery(sql)
                    .setParameter("idpaciente", idpaciente)
                    .getResultList();
            List<Map<String, Object>> eventos = new ArrayList<>();
            for (Object[] registro : registros) {
                Map<String, Object> evento = new LinkedHashMap<>();
                evento.put("tipo", registro[5]);
                evento.put("titulo", registro[6]);
                evento.put("id", registro[0]);
                evento.put("bam", registro[1]);
                evento.put("data", registro[2]);
                evento.put("hora", null);
                evento.put("profissional", registro[3]);
                evento.put("local", registro[4]);
                evento.put("conteudo", "<p>" + registro[7] + "</p>");
                evento.put("status", registro[8]);
                eventos.add(evento);
            }
            return eventos;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> buscarPacientes(String nome, String unidade) {
        String sql = """
                SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns, MAX(b.idbam) AS ultimo_bam,
                       MAX(CAST(b.data AS date)) AS ultima_data
                FROM pacientes p
                JOIN bam b ON b.paciente_idpaciente = p.idpaciente
                WHERE REPLACE(UPPER(p.nome), ' ', '') LIKE REPLACE(UPPER(:nome), ' ', '')
                  
                GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns
                ORDER BY ultimo_bam DESC
                LIMIT 12
                """;
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("nome", nome + "%") ;
        return rows(query, "idpaciente", "nome", "idade", "prontuario", "mae", "cns", "ultimoBam", "ultimaData");
    }

    private List<Map<String, Object>> buscarPacientesPorId(int idpaciente, String unidade) {
        String sql = """
                SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns, MAX(b.idbam) AS ultimo_bam,
                       MAX(CAST(b.data AS date)) AS ultima_data
                FROM pacientes p
                LEFT JOIN bam b ON b.paciente_idpaciente = p.idpaciente
                WHERE p.idpaciente = :idpaciente 
                GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.mae, p.cns
                """;
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("idpaciente", idpaciente);
                
        return rows(query, "idpaciente", "nome", "idade", "prontuario", "mae", "cns", "ultimoBam", "ultimaData");
    }

    private Map<String, Object> buscarPaciente(int idpaciente) {
        String sql = """
                SELECT idpaciente, nome, idade, prontuario, mae, pai, nascimento, cns, cpf, telefone,
                       endereco, numero, bairro, municipio, estado, pacienteespecial
                FROM pacientes WHERE idpaciente = :idpaciente
                """;
        List<Map<String, Object>> pacientes = rows(entityManager.createNativeQuery(sql).setParameter("idpaciente", idpaciente),
                "idpaciente", "nome", "idade", "prontuario", "mae", "pai", "nascimento", "cns", "cpf", "telefone",
                "endereco", "numero", "bairro", "municipio", "estado", "pacienteespecial");
        return pacientes.isEmpty() ? new LinkedHashMap<>() : pacientes.get(0);
    }

    private List<Map<String, Object>> listarBams(int idpaciente, String unidade) {
        boolean filtrarUnidade = !isTodas(unidade);
        String sql = """
                SELECT b.idbam, b.data, b.hora, b.unidade, b.cor, b.internar, b.ematendimento, b.transferido,
                       r.setor, r.especialidade, r.tipochegada, r.tipoocorrencia, r.queixaprincipal, r.obsentrada,
                       r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe,
                       a.risco, a.anamnese, a.comentario, a.hpp, a.pasist, a.padiast, a.fc, a.fr, a.spo, a.tax, a.peso, a.glasgow, a.niveldor,
                       t.diagnostico_provisorio, t.exames_solicitados, t.prescricao_inicial, t.destino, t.encaminhamento,
                       t.tipo_alta, t.tratado, t.obs_saida, t.medicoecrm
                FROM bam b
                JOIN registro r ON r.idregistro = b.registro_idregistro
                JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao
                JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                WHERE b.paciente_idpaciente = :idpaciente
                """ + (filtrarUnidade ? " AND b.unidade = :unidade " : "") + """
                ORDER BY b.idbam DESC
                """;
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("idpaciente", idpaciente);
        if (filtrarUnidade) {
            query.setParameter("unidade", unidade);
        }
        return rows(query,
                "idbam", "data", "hora", "unidade", "cor", "internar", "ematendimento", "transferido",
                "setor", "especialidade", "tipochegada", "tipoocorrencia", "queixaPrincipal", "obsentrada",
                "temlabo", "temrx", "temtomo", "temmed", "temenfe",
                "risco", "anamnese", "comentario", "hpp", "pasist", "padiast", "fc", "fr", "spo", "tax", "peso", "glasgow", "niveldor",
                "diagnosticoProvisorio", "examesSolicitados", "prescricaoInicial", "destino", "encaminhamento",
                "tipoAlta", "tratado", "obsSaida", "medicoecrm");
    }

    private List<Map<String, Object>> listarAtendimentos(int bam) {
        String sql = "SELECT idatendimento, data, hora, nomedomedico, NULL AS local, relato, receita FROM atendimento WHERE bam = :bam ORDER BY data DESC, hora DESC";
        return eventos(sql, bam, "ATENDIMENTO", "Atendimento medico", "idatendimento", "data", "hora", "nomedomedico", null, "relato", "receita");
    }

    private List<Map<String, Object>> listarPrescricoes(int bam) {
        String sql = "SELECT idprescricao, data, hora, medicoq, localsolicitante, prescricao, medicacao, feito, inativo FROM prescricao WHERE bam = :bam ORDER BY data DESC, hora DESC";
        return eventos(sql, bam, "PRESCRICAO", "Prescricao/medicamentos", "idprescricao", "data", "hora", "medicoq", "localsolicitante", "prescricao", "medicacao", "feito", "inativo");
    }

    private List<Map<String, Object>> listarPrescricoesEstruturadas(int bam) {
        String sql = """
                SELECT iditem, criado_em, NULL AS hora, prescrito_por, unidade, medicamento, dose, via,
                       frequencia, duracao, observacao, status, motivo_suspensao
                FROM prescricao_item_estruturado
                WHERE idbam = :bam
                ORDER BY criado_em DESC, iditem DESC
                """;
        return eventos(sql, bam, "PRESCRICAO_ESTRUTURADA", "Prescricao estruturada", "iditem", "criado_em", "hora", "prescrito_por", "unidade", "medicamento", "dose", "via", "frequencia", "duracao", "observacao", "status", "motivo_suspensao");
    }

    private List<Map<String, Object>> listarPedidos(int bam) {
        String sql = "SELECT idpedidomedico, dt, hr, medico, local, pedido, obs, efetuado, coletar, confirmado, internado, emergencia FROM pedidomedico2 WHERE bam = :bam ORDER BY dt DESC, hr DESC";
        return eventos(sql, bam, "PEDIDO", "Pedido de exame/medicacao", "idpedidomedico", "dt", "hr", "medico", "local", "pedido", "obs", "efetuado", "coletar", "confirmado", "internado", "emergencia");
    }

    private List<Map<String, Object>> listarResultadosLaboratorio(int bam) {
        String sql = "SELECT idpedidoresultado, dt, hr, medico, local, descricao, grupopedidos, arquivo FROM pedidoresultadolaboratorio WHERE bam = :bam ORDER BY dt DESC, hr DESC";
        return eventos(sql, bam, "RESULTADO_LAB", "Resultado de laboratorio", "idpedidoresultado", "dt", "hr", "medico", "local", "descricao", "grupopedidos", "arquivo");
    }

    private List<Map<String, Object>> listarEvolucoesMedicas(int bam) {
        String sql = "SELECT idevolucao, data, horaev, medico, local, relato, pedido, pedidodomed, inativo FROM evolucaopaciente WHERE nbam = :bam ORDER BY data DESC, horaev DESC";
        return eventos(sql, bam, "EVOLUCAO_MEDICA", "Evolucao medica", "idevolucao", "data", "horaev", "medico", "local", "relato", "pedido", "pedidodomed", "inativo");
    }

    private List<Map<String, Object>> listarEvolucoesEnfermagem(int bam) {
        String sql = """
                SELECT idevolucao, data, hora, enfermeiro, local, relato, pedido, pedidodomed,
                       CASE
                           WHEN UPPER(TRIM(COALESCE(quem, ''))) IN ('', 'ENFERMEIRO', 'TECNICO', 'TÉCNICO', 'TEC')
                                OR quem IS NULL
                           THEN 'EVOLUCAO_ENFERMAGEM'
                           ELSE 'EVOLUCAO_MULTIDISCIPLINAR'
                       END AS tipo_evento
                FROM evolucaopacienteenf
                WHERE nbam = :bam
                ORDER BY data DESC, hora DESC
                """;

        try {
            List<Map<String, Object>> registros = rows(entityManager.createNativeQuery(sql).setParameter("bam", bam),
                    "idevolucao", "data", "hora", "enfermeiro", "local", "relato", "pedido", "pedidodomed", "tipoEvento");
            List<Map<String, Object>> eventos = new ArrayList<>();

            for (Map<String, Object> registro : registros) {
                String tipo = String.valueOf(registro.get("tipoEvento"));
                Map<String, Object> evento = new LinkedHashMap<>();
                evento.put("tipo", tipo);
                evento.put("titulo", "EVOLUCAO_MULTIDISCIPLINAR".equals(tipo)
                        ? "Evolucao multidisciplinar"
                        : "Evolucao de enfermagem");
                evento.put("id", registro.get("idevolucao"));
                evento.put("bam", bam);
                evento.put("data", registro.get("data"));
                evento.put("hora", registro.get("hora"));
                evento.put("profissional", registro.get("enfermeiro"));
                evento.put("local", registro.get("local"));
                evento.put("conteudo", montarConteudo(registro,
                        new String[] { "idevolucao", "data", "hora", "enfermeiro", "local", "relato", "pedido", "pedidodomed" }, 5));
                evento.put("status", "Ativa");
                eventos.add(evento);
            }
            return eventos;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> listarAnamneseInternacao(int bam) {
        String sql = "SELECT idanamneseinternacao, data, NULL AS hora, medico, NULL AS local, frente, verso, crm FROM anamneseinternacao WHERE nbam = :bam ORDER BY idanamneseinternacao DESC";
        return eventos(sql, bam, "ANAMNESE_INTERNACAO", "Anamnese de internacao", "idanamneseinternacao", "data", "hora", "medico", "local", "frente", "verso", "crm");
    }

    private List<Map<String, Object>> listarInternacoes(int bam) {
        String sql = "SELECT idninternado, datainternacao, NULL AS hora, medicoqueinternou, local, obs, cid, datasaida, motivodaalta, medicodeualta FROM internacao WHERE bam = CAST(:bam AS text) ORDER BY datainternacao DESC";
        return eventos(sql, bam, "INTERNACAO", "Internacao", "idninternado", "datainternacao", "hora", "medicoqueinternou", "local", "obs", "cid", "datasaida", "motivodaalta", "medicodeualta");
    }

    private List<Map<String, Object>> listarNir(int bam) {
        String sql = """
                SELECT n.idninternado, n.diainternacao, NULL AS hora, n.quemalocou,
                       COALESCE(s.setor, n.tipo) AS local, n.diagnostico, n.leitoocupado, n.alta, n.motivosaida, n.pendencia
                FROM novointernado n
                LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso
                WHERE n.nbam = :bam
                ORDER BY n.diainternacao DESC
                """;
        return eventos(sql, bam, "NIR", "NIR/leito", "idninternado", "diainternacao", "hora", "quemalocou", "local", "diagnostico", "leitoocupado", "alta", "motivosaida", "pendencia");
    }

    private List<Map<String, Object>> listarResumoAlta(int bam) {
        String sql = "SELECT idresumoalta, dataalta, horaobito, nome, NULL AS local, relato, diagfinal, cid2 FROM resumodealta WHERE bam = CAST(:bam AS text) ORDER BY idresumoalta DESC";
        return eventos(sql, bam, "RESUMO_ALTA", "Resumo de alta", "idresumoalta", "dataalta", "horaobito", "nome", "local", "relato", "diagfinal", "cid2");
    }

    private List<Map<String, Object>> listarObitos(int bam) {
        List<Map<String, Object>> obitos = new ArrayList<>();
        obitos.addAll(eventos("""
                SELECT idresumoalta, COALESCE(dataobito, dataalta), horaobito, NULL AS profissional, NULL AS local, diagfinal, cid2
                FROM resumodealta
                WHERE bam = CAST(:bam AS text) AND obito = true AND COALESCE(status, 'ATIVO') <> 'CANCELADO'
                """, bam, "OBITO", "Obito (resumo de alta)",
                "idresumoalta", "dataobito", "horaobito", "profissional", "local", "diagfinal", "cid2"));
        obitos.addAll(eventos("""
                SELECT b.idbam, b.data, b.hora, t.medicoecrm, b.unidade, t.tipo_alta, t.obs_saida
                FROM bam b JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                WHERE b.idbam = :bam AND t.tipo_alta ILIKE '%bito%'
                """, bam, "OBITO", "Obito (desfecho do atendimento)",
                "idbam", "data", "hora", "medicoecrm", "unidade", "tipo_alta", "obs_saida"));
        obitos.addAll(eventos("""
                SELECT idninternado, datasaida, NULL AS hora, medicodeualta, local, motivodaalta, cid
                FROM internacao
                WHERE bam = CAST(:bam AS text) AND motivodaalta ILIKE '%bito%'
                """, bam, "OBITO", "Obito (internacao)",
                "idninternado", "datasaida", "hora", "medicodeualta", "local", "motivodaalta", "cid"));
        obitos.addAll(eventos("""
                SELECT n.idninternado, NULL AS data, NULL AS hora, n.quemdeualta, COALESCE(s.setor, n.tipo) AS local, n.motivosaida
                FROM novointernado n
                LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso
                WHERE n.nbam = :bam AND n.motivosaida ILIKE '%bito%'
                """, bam, "OBITO", "Obito (saida do leito/NIR)",
                "idninternado", "data", "hora", "quemdeualta", "local", "motivosaida"));
        // Tabela mais recente (V186): fica por ultimo para nao afetar as demais consultas.
        obitos.addAll(eventos("""
                SELECT id, dataobito, horaobito, medicoassistente, setor, causabasica, causaterminal, numerodo
                FROM sumarioobito
                WHERE bam = CAST(:bam AS text)
                """, bam, "OBITO", "Sumario de obito",
                "id", "dataobito", "horaobito", "medicoassistente", "setor", "causabasica", "causaterminal", "numerodo"));
        return obitos;
    }

    private List<Map<String, Object>> listarAnotacoesEnfermagem(int bam) {
        String sql = """
                SELECT id, criado_em, NULL AS hora, criado_por, unidade, anotacao, status
                FROM anotacao_enfermagem
                WHERE idbam = :bam
                ORDER BY criado_em DESC, id DESC
                """;
        return eventos(sql, bam, "ANOTACAO_ENFERMAGEM", "Anotacao de enfermagem", "id", "criado_em", "hora", "criado_por", "unidade", "anotacao", "status");
    }

    /**
     * Cirurgias do paciente.
     *
     * O motivo do cancelamento e da suspensao vive em observacao, que o modulo
     * cirurgico concatena ao cancelar: por isso a coluna entra no conteudo do
     * evento em vez de ficar so no log.
     */
    private List<Map<String, Object>> listarCirurgias(int idpaciente) {
        String sql = "SELECT idcirurgia, data_prevista, NULL AS hora, nomecirurgiao, unidade, numero, status, "
                + "especialidade, carater, cid, diagnostico, observacao FROM cirurgia_agendamento "
                + "WHERE idpaciente = :idpaciente ORDER BY data_prevista DESC";
        return eventosDoPaciente(sql, idpaciente, "CIRURGIA", "Cirurgia",
                "idcirurgia", "data_prevista", "hora", "nomecirurgiao", "unidade",
                "numero", "status", "especialidade", "carater", "cid", "diagnostico", "observacao");
    }

    private List<Map<String, Object>> listarDemandasAssistenciaisCirurgicas(int idpaciente) {
        String sql = "SELECT idevidencia, data_registro, NULL AS hora, responsavel, unidade, modulo, status, tipo, referencia, descricao "
                + "FROM evidencia_assistencial WHERE idpaciente = :idpaciente AND origem_modulo = 'CENTRO_CIRURGICO' "
                + "ORDER BY data_registro DESC";
        return eventosDoPaciente(sql, idpaciente, "APOIO_CIRURGICO", "Apoio cirurgico",
                "idevidencia", "data_registro", "hora", "responsavel", "unidade",
                "modulo", "status", "tipo", "referencia", "descricao");
    }

    /**
     * Passagens do paciente pela fila cirurgica, com o motivo do cancelamento.
     *
     * Cancelar um item agendado exclui a cirurgia do mapa, entao sem este bloco o
     * cancelamento e o motivo desapareceriam do historico junto com ela.
     */
    private List<Map<String, Object>> listarListaEsperaCirurgica(int idpaciente) {
        String sql = "SELECT idespera, data_inclusao, NULL AS hora, usuario_criador, unidade, status, "
                + "procedimento_descricao, procedimento_codigo, especialidade, prioridade, "
                + "justificativa_prioridade, data_prevista, observacao FROM cirurgia_lista_espera "
                + "WHERE idpaciente = :idpaciente ORDER BY data_inclusao DESC";
        return eventosDoPaciente(sql, idpaciente, "LISTA_ESPERA_CIRURGICA", "Lista de espera cirurgica",
                "idespera", "data_inclusao", "hora", "usuario_criador", "unidade", "status",
                "procedimento_descricao", "procedimento_codigo", "especialidade", "prioridade",
                "justificativa_prioridade", "data_prevista", "observacao");
    }

    /**
     * Mesma montagem de evento do helper por BAM, chaveada por paciente.
     *
     * O campo bam sai nulo de proposito: o evento existe mesmo sem atendimento
     * aberto, e forcar um BAM ali daria a entender um vinculo que nao existe. Os
     * nomes em fields acompanham a ordem das colunas do SELECT, que e como rows()
     * casa valor e chave.
     */
    private List<Map<String, Object>> eventosDoPaciente(String sql, int idpaciente, String tipo, String titulo,
            String... fields) {
        try {
            Query query = entityManager.createNativeQuery(sql).setParameter("idpaciente", idpaciente);
            List<Map<String, Object>> base = rows(query, fields);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : base) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("tipo", tipo);
                event.put("titulo", titulo);
                event.put("id", row.get(fields[0]));
                event.put("bam", null);
                event.put("data", row.get(fields[1]));
                event.put("hora", row.get(fields[2]));
                event.put("profissional", row.get(fields[3]));
                event.put("local", row.get(fields[4]));
                event.put("conteudo", montarConteudo(row, fields, 5));
                event.put("status", montarStatus(row));
                result.add(event);
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> eventos(String sql, int bam, String tipo, String titulo, String... fields) {
        try {
            List<Map<String, Object>> base = rows(entityManager.createNativeQuery(sql).setParameter("bam", bam), fields);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : base) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("tipo", tipo);
                event.put("titulo", titulo);
                event.put("id", row.get(fields[0]));
                event.put("bam", bam);
                event.put("data", row.get(fields.length > 1 ? fields[1] : fields[0]));
                event.put("hora", row.get(fields.length > 2 ? fields[2] : fields[0]));
                event.put("profissional", row.get(fields.length > 3 ? fields[3] : fields[0]));
                event.put("local", fields.length > 4 && fields[4] != null ? row.get(fields[4]) : null);
                event.put("conteudo", montarConteudo(row, fields, 5));
                event.put("status", montarStatus(row));
                result.add(event);
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String montarConteudo(Map<String, Object> row, String[] fields, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < fields.length; i++) {
            String field = fields[i];
            if (field == null) continue;
            Object value = row.get(field);
            if (value == null || String.valueOf(value).isBlank()) continue;
            builder.append("<p><strong>").append(label(field)).append(":</strong> ").append(value).append("</p>");
        }
        return builder.length() == 0 ? "<p>Sem detalhe registrado.</p>" : builder.toString();
    }

    private String montarStatus(Map<String, Object> row) {
        List<String> status = new ArrayList<>();
        for (String key : List.of("feito", "inativo", "efetuado", "coletar", "confirmado", "internado", "emergencia", "alta")) {
            if (row.containsKey(key)) status.add(label(key) + ": " + row.get(key));
        }
        return String.join(" | ", status);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Query query, String... keys) {
        List<Object[]> records = query.getResultList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : records) {
            Object[] values = keys.length == 1 ? new Object[] { item } : (Object[]) item;
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                row.put(keys[i], i < values.length ? normalizarValor(values[i]) : null);
            }
            result.add(row);
        }
        return result;
    }

    private Object normalizarValor(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof Boolean) return value;
        return String.valueOf(value);
    }

    private long contarTipo(List<Map<String, Object>> eventos, String tipo) {
        return eventos.stream().filter(item -> tipo.equals(item.get("tipo"))).count();
    }

    private long contarTipos(List<Map<String, Object>> eventos, List<String> tipos) {
        return eventos.stream().filter(item -> tipos.contains(String.valueOf(item.get("tipo")))).count();
    }

    private boolean isTodas(String unidade) {
        return unidade == null || unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade) || "TODOS".equalsIgnoreCase(unidade);
    }

    private String label(String field) {
        return switch (field) {
            case "relato" -> "Relato";
            case "receita" -> "Receita";
            case "prescricao" -> "Prescricao";
            case "medicacao" -> "Medicacao";
            case "medicamento" -> "Medicamento";
            case "dose" -> "Dose";
            case "via" -> "Via";
            case "frequencia" -> "Frequencia";
            case "duracao" -> "Duracao";
            case "status" -> "Status";
            case "motivo_suspensao" -> "Motivo da suspensao";
            case "pedido" -> "Pedido";
            case "obs" -> "Observacao";
            case "grupopedidos" -> "Grupo de pedidos";
            case "arquivo" -> "Arquivo";
            case "cid", "cid2" -> "CID";
            case "datasaida" -> "Data de saida";
            case "motivodaalta" -> "Motivo da alta";
            case "medicodeualta" -> "Medico da alta";
            case "leitoocupado" -> "Leito";
            case "pendencia" -> "Pendencia";
            case "diagfinal" -> "Diagnostico final";
            case "tipo_alta" -> "Tipo de alta";
            case "obs_saida" -> "Observacao da saida";
            case "motivosaida" -> "Motivo da saida";
            case "causabasica" -> "Causa basica";
            case "causaterminal" -> "Causa terminal";
            case "numerodo" -> "Numero da DO";
            default -> field;
        };
    }
}
