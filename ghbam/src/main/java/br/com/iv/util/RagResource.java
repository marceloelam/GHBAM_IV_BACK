package br.com.iv.util;

import br.com.iv.entity.Bam;
import br.com.iv.entity.DocumentoEmbedding;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.Bulario;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.BularioRepository;
import br.com.iv.repository.DocumentoEmbeddingRepository;
import br.com.iv.repository.PacientesRepository;
import br.com.iv.entity.RagAprendizado;
import br.com.iv.repository.RagAprendizadoRepository;
import br.com.iv.service.EmbeddingService;
import br.com.iv.service.RagService;
import br.com.iv.service.WebSearchService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/rag")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RagResource {

    private static final Logger LOG = Logger.getLogger(RagResource.class);

    @Inject
    RagService ragService;

    @Inject
    DocumentoEmbeddingRepository documentoRepository;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    PacientesRepository pacientesRepository;

    @Inject
    BamRepository bamRepository;

    @Inject
    BularioRepository bularioRepository;

    @Inject
    WebSearchService webSearchService;

    @Inject
    RagAprendizadoRepository aprendizadoRepository;

    @GET
    @Path("/pacientes")
    public Response buscarPacientes(@QueryParam("q") String q) {
        try {
            if (q == null || q.isBlank()) {
                return Response.ok(List.of()).build();
            }
            List<Pacientes> pacientes = pacientesRepository.buscarPorNomeInteligente(q, 20);
            List<Map<String, Object>> resultado = pacientes.stream().map(p -> {
                List<Bam> bams = bamRepository.filtrarPorIdDoPciente(p.getIdpaciente());
                int bam = bams.isEmpty() ? 0 : bams.get(0).getIdbam();
                return Map.<String, Object>of(
                    "idpaciente", p.getIdpaciente(),
                    "nome", p.getNome(),
                    "bam", bam
                );
            }).collect(Collectors.toList());
            return Response.ok(resultado).build();
        } catch (Exception e) {
            LOG.error("Erro em /api/rag/pacientes", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName())))
                .build();
        }
    }

    @SuppressWarnings("unchecked")
    @POST
    @Path("/perguntar")
    public Response perguntar(Map<String, Object> body) {
        try {
            String pergunta = (String) body.get("pergunta");
            String setor = (String) body.getOrDefault("setor", "");
            int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 10;
            Integer idpaciente = body.containsKey("idpaciente") ? ((Number) body.get("idpaciente")).intValue() : null;
            Integer bam = body.containsKey("bam") && body.get("bam") != null ? Integer.parseInt(body.get("bam").toString()) : null;
            double temperatura = body.containsKey("temperatura") ? ((Number) body.get("temperatura")).doubleValue() : 0.7;
            boolean usarWeb = body.containsKey("usarWeb") && Boolean.TRUE.equals(body.get("usarWeb"));
            List<Map<String, String>> historico = (List<Map<String, String>>) body.get("historico");

            if (pergunta == null || pergunta.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campo 'pergunta' e obrigatorio"))
                    .build();
            }

            RagService.RagAnswer answer = ragService.perguntarComContextoComMeta(pergunta, setor, topK, idpaciente, historico, temperatura, usarWeb, bam);
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("resposta", answer.resposta);
            response.put("usouWeb", answer.usouWeb);
            response.put("modo", "rag");
            if (answer.pacienteDetectado != null) {
                response.put("pacienteDetectado", answer.pacienteDetectado);
            }
            if (answer.idaprendizado > 0) {
                response.put("idaprendizado", answer.idaprendizado);
            }
            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.error("Erro em /api/rag/perguntar", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName())))
                .build();
        }
    }

    @SuppressWarnings("unchecked")
    @POST
    @Path("/chat")
    public Response chat(Map<String, Object> body) {
        try {
            List<Map<String, String>> mensagens = (List<Map<String, String>>) body.get("mensagens");
            String setor = (String) body.getOrDefault("setor", "");
            int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 10;
            Integer idpaciente = body.containsKey("idpaciente") ? ((Number) body.get("idpaciente")).intValue() : null;

            if (mensagens == null || mensagens.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campo 'mensagens' e obrigatorio"))
                    .build();
            }

            String resposta = ragService.perguntarComContextoChat(mensagens, setor, topK, idpaciente);
            return Response.ok(Map.of(
                "resposta", resposta,
                "modo", "rag"
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/indexar")
    @Transactional
    public Response indexar(Map<String, Object> body) {
        try {
            String titulo = (String) body.get("titulo");
            String conteudo = (String) body.get("conteudo");
            String tipo = (String) body.getOrDefault("tipo", "texto");
            String setor = (String) body.getOrDefault("setor", "");
            Integer idpaciente = body.containsKey("idpaciente") ? ((Number) body.get("idpaciente")).intValue() : null;
            Integer idprontuario = body.containsKey("idprontuario") ? ((Number) body.get("idprontuario")).intValue() : null;
            String metadados = body.containsKey("metadados") ? body.get("metadados").toString() : null;

            if (titulo == null || titulo.isBlank() || conteudo == null || conteudo.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campos 'titulo' e 'conteudo' sao obrigatorios"))
                    .build();
            }

            ragService.indexarDocumento(titulo, conteudo, tipo, setor, idpaciente, idprontuario, metadados);
            return Response.ok(Map.of("mensagem", "Documento indexado com sucesso")).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro ao indexar: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/documentos")
    public Response listarDocumentos(
            @QueryParam("tipo") String tipo,
            @QueryParam("setor") String setor,
            @QueryParam("pagina") @DefaultValue("0") int pagina,
            @QueryParam("limite") @DefaultValue("20") int limite) {
        try {
            List<DocumentoEmbedding> documentos;
            long total;

            if (tipo != null && !tipo.isBlank()) {
                documentos = documentoRepository.buscarPorTipo(tipo);
                total = documentoRepository.contarPorTipo(tipo);
            } else if (setor != null && !setor.isBlank()) {
                documentos = documentoRepository.buscarPorSetor(setor);
                total = documentos.size();
            } else {
                documentos = documentoRepository.findAll().page(pagina, limite).list();
                total = documentoRepository.count();
            }

            return Response.ok(Map.of(
                "documentos", documentos.stream().map(d -> Map.of(
                    "idragdocumento", d.getIdragdocumento(),
                    "titulo", d.getTitulo(),
                    "tipo", d.getTipo(),
                    "setor", d.getSetor() != null ? d.getSetor() : "",
                    "idpaciente", d.getIdpaciente(),
                    "criadoem", d.getCriadoem() != null ? d.getCriadoem().toString() : null
                )).toList(),
                "total", total
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @DELETE
    @Path("/documentos/{id}")
    @Transactional
    public Response removerDocumento(@PathParam("id") int id) {
        try {
            boolean removido = documentoRepository.deleteById((long) id);
            if (removido) {
                return Response.ok(Map.of("mensagem", "Documento removido com sucesso")).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("erro", "Documento nao encontrado"))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/indexar-lote")
    @Transactional
    public Response indexarLote(List<Map<String, Object>> documentos) {
        try {
            if (documentos == null || documentos.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Lista de documentos vazia"))
                    .build();
            }

            int sucesso = 0;
            int erros = 0;

            for (Map<String, Object> doc : documentos) {
                try {
                    String titulo = (String) doc.get("titulo");
                    String conteudo = (String) doc.get("conteudo");
                    if (titulo == null || conteudo == null) continue;

                    String tipo = (String) doc.getOrDefault("tipo", "texto");
                    String setor = (String) doc.getOrDefault("setor", "");
                    Integer idpaciente = doc.containsKey("idpaciente") ? ((Number) doc.get("idpaciente")).intValue() : null;
                    Integer idprontuario = doc.containsKey("idprontuario") ? ((Number) doc.get("idprontuario")).intValue() : null;
                    String metadados = doc.containsKey("metadados") ? doc.get("metadados").toString() : null;

                    ragService.indexarDocumento(titulo, conteudo, tipo, setor, idpaciente, idprontuario, metadados);
                    sucesso++;
                } catch (Exception e) {
                    erros++;
                }
            }

            return Response.ok(Map.of(
                "mensagem", sucesso + " documentos indexados com sucesso",
                "sucesso", sucesso,
                "erros", erros
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/indexar-bulario")
    // Sem @Transactional aqui de proposito: sao milhares de medicamentos e cada embedding leva ~0,3 s,
    // o que estourava o timeout de 120 s da transacao unica e desfazia tudo. Cada documento e gravado
    // na transacao do proprio RagService.indexarDocumento, entao o progresso fica salvo.
    public Response indexarBulario(Map<String, Object> body) {
        try {
            boolean forcar = body.containsKey("forcar") && Boolean.TRUE.equals(body.get("forcar"));
            int total = 0;
            int ignorados = 0;
            int erros = 0;
            String primeiroErro = null;

            List<Bulario> todos = bularioRepository.findAll().list();
            for (Bulario b : todos) {
                try {
                    if (b.getNome() == null || b.getNome().isBlank()) continue;

                    if (!forcar) {
                        long existentes = documentoRepository.find("titulo = ?1 and tipo = 'medicamento'", b.getNome()).count();
                        if (existentes > 0) {
                            ignorados++;
                            continue;
                        }
                    }

                    StringBuilder conteudo = new StringBuilder();
                    conteudo.append("Medicamento: ").append(b.getNome()).append("\n");
                    if (b.getPrincipioAtivo() != null && !b.getPrincipioAtivo().isBlank())
                        conteudo.append("Principio Ativo: ").append(b.getPrincipioAtivo()).append("\n");
                    if (b.getTarja() != null && !b.getTarja().isBlank())
                        conteudo.append("Tarja: ").append(b.getTarja()).append("\n");
                    if (b.getResumo() != null && !b.getResumo().isBlank() && !b.getResumo().equals("."))
                        conteudo.append("Resumo: ").append(b.getResumo()).append("\n");
                    if (b.getIndicacoes() != null && !b.getIndicacoes().isBlank() && !b.getIndicacoes().equals("."))
                        conteudo.append("Indicacoes: ").append(b.getIndicacoes()).append("\n");
                    if (b.getPosologia() != null && !b.getPosologia().isBlank() && !b.getPosologia().equals("."))
                        conteudo.append("Posologia: ").append(b.getPosologia()).append("\n");
                    if (b.getContraindicacoes() != null && !b.getContraindicacoes().isBlank() && !b.getContraindicacoes().equals("."))
                        conteudo.append("Contraindicacoes: ").append(b.getContraindicacoes()).append("\n");
                    if (b.getEfeitosAdversos() != null && !b.getEfeitosAdversos().isBlank() && !b.getEfeitosAdversos().equals("."))
                        conteudo.append("Efeitos Adversos: ").append(b.getEfeitosAdversos()).append("\n");
                    if (b.getInteracoes() != null && !b.getInteracoes().isBlank() && !b.getInteracoes().equals("."))
                        conteudo.append("Interacoes: ").append(b.getInteracoes()).append("\n");
                    if (b.getPrecaucoes() != null && !b.getPrecaucoes().isBlank() && !b.getPrecaucoes().equals("."))
                        conteudo.append("Precaucoes: ").append(b.getPrecaucoes()).append("\n");

                    ragService.indexarDocumento(b.getNome(), conteudo.toString(), "medicamento", "farmacologia",
                            null, null, "{\"id_bulario\":" + b.getId() + "}");
                    total++;
                } catch (Exception e) {
                    erros++;
                    // registra so o primeiro erro com stack e guarda o motivo para a tela;
                    // os demais costumam ser a mesma causa (ex.: servico de embedding fora do ar)
                    if (primeiroErro == null) {
                        primeiroErro = e.getClass().getSimpleName() + ": " + e.getMessage();
                        LOG.error("Falha ao indexar medicamento id=" + b.getId() + " em /api/rag/indexar-bulario", e);
                    }
                }
            }

            if (erros > 0) {
                LOG.warn("Indexacao do bulario: " + total + " indexados, " + erros + " erros. Primeiro erro: " + primeiroErro);
            }

            return Response.ok(Map.of(
                "mensagem", total + " medicamentos indexados" +
                            (ignorados > 0 ? ", " + ignorados + " ja existentes ignorados" : "") +
                            (erros > 0 ? ", " + erros + " erros (motivo: " + primeiroErro + ")" : ""),
                "indexados", total,
                "ignorados", ignorados,
                "erros", erros,
                "motivoErro", primeiroErro == null ? "" : primeiroErro
            )).build();

        } catch (Exception e) {
            LOG.error("Erro em /api/rag/indexar-bulario", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/indexar-json")
    @Transactional
    public Response indexarJson(List<Map<String, Object>> documentos) {
        try {
            if (documentos == null || documentos.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Lista de documentos vazia"))
                    .build();
            }

            int sucesso = 0;
            int erros = 0;

            for (Map<String, Object> doc : documentos) {
                try {
                    String titulo = (String) doc.get("titulo");
                    String conteudo = (String) doc.get("conteudo");
                    if (titulo == null || titulo.isBlank() || conteudo == null || conteudo.isBlank()) continue;

                    String tipo = (String) doc.getOrDefault("tipo", "texto");
                    String setor = (String) doc.getOrDefault("setor", "");

                    ragService.indexarDocumento(titulo, conteudo, tipo, setor, null, null, null);
                    sucesso++;
                } catch (Exception e) {
                    erros++;
                }
            }

            return Response.ok(Map.of(
                "mensagem", sucesso + " documentos indexados com sucesso" + (erros > 0 ? ", " + erros + " erros" : ""),
                "sucesso", sucesso,
                "erros", erros
            )).build();

        } catch (Exception e) {
            LOG.error("Erro em /api/rag/indexar-json", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/buscar")
    public Response buscar(Map<String, Object> body) {
        try {
            String texto = (String) body.get("texto");
            String setor = (String) body.getOrDefault("setor", "");
            int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 10;

            if (texto == null || texto.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campo 'texto' e obrigatorio"))
                    .build();
            }

            float[] queryEmbedding = embeddingService.gerarEmbedding(texto);
            String embeddingStr = embeddingService.embeddingParaString(queryEmbedding);

            List<Object[]> resultados;
            if (setor != null && !setor.isBlank()) {
                resultados = documentoRepository.buscarPorSemelhancaESetor(embeddingStr, setor, topK);
            } else {
                resultados = documentoRepository.buscarPorSemelhanca(embeddingStr, topK);
            }

            return Response.ok(Map.of(
                "resultados", resultados.stream().map(r -> {
                    String conteudo = r[2] != null ? r[2].toString() : "";
                    String titulo = r[1] != null ? r[1].toString() : "";
                    return Map.of(
                        "idragdocumento", r[0],
                        "titulo", titulo,
                        "conteudo", conteudo.length() > 500 ? conteudo.substring(0, 500) + "..." : conteudo,
                        "tipo", r[3] != null ? r[3].toString() : "",
                        "setor", r[4] != null ? r[4].toString() : "",
                        "similaridade", r[9] != null ? r[9].toString() : "0"
                    );
                }).toList(),
                "total", resultados.size()
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/status")
    public Response status() {
        try {
            long total = documentoRepository.count();
            return Response.ok(Map.of(
                "total_documentos", total,
                "status", "ok"
            )).build();
        } catch (Exception e) {
            LOG.error("Erro em /api/rag/status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/buscar-web")
    public Response buscarWeb(Map<String, Object> body) {
        try {
            String query = (String) body.get("query");
            if (query == null || query.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campo 'query' e obrigatorio"))
                    .build();
            }
            String resultado = webSearchService.buscar(query);
            return Response.ok(Map.of("resultado", resultado)).build();
        } catch (Exception e) {
            LOG.error("Erro em /api/rag/buscar-web", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/feedback")
    public Response feedback(Map<String, Object> body) {
        try {
            Object idObj = body.get("id");
            Object fbObj = body.get("feedback");
            if (idObj == null || fbObj == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Campos 'id' e 'feedback' sao obrigatorios"))
                    .build();
            }
            int id = ((Number) idObj).intValue();
            int fb = ((Number) fbObj).intValue();
            ragService.registrarFeedback(id, fb);
            return Response.ok(Map.of("mensagem", "Feedback registrado")).build();
        } catch (Exception e) {
            LOG.error("Erro em /api/rag/feedback", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/aprendizado")
    public Response listarAprendizado() {
        try {
            List<RagAprendizado> lista = aprendizadoRepository.listarComFeedback();
            return Response.ok(Map.of("aprendizados", lista)).build();
        } catch (Exception e) {
            LOG.error("Erro em /api/rag/aprendizado", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("erro", "Erro: " + e.getMessage()))
                .build();
        }
    }

    @SuppressWarnings("unchecked")
    @POST
    @Path("/perguntar-stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Blocking
    public Multi<String> perguntarStream(Map<String, Object> body) {
        String pergunta = (String) body.get("pergunta");
        String setor = (String) body.getOrDefault("setor", "");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 10;
        Integer idpaciente = body.containsKey("idpaciente") ? ((Number) body.get("idpaciente")).intValue() : null;
        Integer bam = body.containsKey("bam") && body.get("bam") != null ? Integer.parseInt(body.get("bam").toString()) : null;
        double temperatura = body.containsKey("temperatura") ? ((Number) body.get("temperatura")).doubleValue() : 0.7;
        List<Map<String, String>> historico = (List<Map<String, String>>) body.get("historico");

        if (pergunta == null || pergunta.isBlank()) {
            return Multi.createFrom().item("{\"erro\":\"Pergunta obrigatoria\"}");
        }

        return Multi.createFrom().<String>emitter(em -> {
            try {
                RagService.RagAnswer answer = ragService.perguntarComContextoStream(
                    pergunta, setor, topK, idpaciente, historico, temperatura,
                    status -> em.emit("{\"status\":\"" + status + "\"}"),
                    bam
                );

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.node.ObjectNode done = mapper.createObjectNode();
                done.put("status", "done");
                done.put("resposta", answer.resposta);
                done.put("usouWeb", answer.usouWeb);
                if (answer.pacienteDetectado != null) {
                    done.put("pacienteDetectado", answer.pacienteDetectado);
                }
                if (answer.idaprendizado > 0) {
                    done.put("idaprendizado", answer.idaprendizado);
                }
                em.emit(mapper.writeValueAsString(done));
                em.complete();
            } catch (Exception e) {
                LOG.error("Erro no /perguntar-stream", e);
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.node.ObjectNode err = mapper.createObjectNode();
                    err.put("status", "erro");
                    err.put("mensagem", e.getMessage() != null ? e.getMessage() : "Erro interno");
                    em.emit(mapper.writeValueAsString(err));
                } catch (Exception ex) {
                    em.emit("{\"status\":\"erro\",\"mensagem\":\"Erro interno\"}");
                }
                em.complete();
            }
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }
}

