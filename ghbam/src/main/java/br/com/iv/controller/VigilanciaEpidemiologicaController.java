package br.com.iv.controller;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import br.com.iv.entity.NotificacaoSinan;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.NotificacaoSinanService;
import br.com.iv.service.OcorrenciaSistemaService;

/** Sinais para investigacao epidemiologica; nao confirma surto ou diagnostico. */
@Path("vigilancia-epidemiologica")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VigilanciaEpidemiologicaController {
    private static final Logger LOG = Logger.getLogger(VigilanciaEpidemiologicaController.class);
    private static final String ACENTOS = "áàâãäéèêëíìîïóòôõöúùûüçñÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑ";
    private static final String SEM_ACENTOS = "aaaaaeeeeiiiiooooouuuucnAAAAAEEEEIIIIOOOOOUUUUCN";
    @Inject EntityManager em;
    @Inject OcorrenciaSistemaService ocorrenciaSistemaService;
    @Inject NotificacaoSinanService notificacaoSinanService;
    @Inject SecurityPermissionService securityPermissionService;
    @Context ContainerRequestContext requestContext;

    @GET
    @Transactional
    public Response analisar(@QueryParam("unidade") String unidade) {
        try {
        garantirEstrutura();
        List<Map<String, Object>> regras = regras(unidade);
        List<Map<String, Object>> sinais = new ArrayList<>();
        for (Map<String, Object> regra : regras) {
            if (!Boolean.TRUE.equals(regra.get("ativo"))) continue;
            int janela = numero(regra.get("janelaDias"), 7);
            int minimo = numero(regra.get("minimoCasos"), 3);
            double fator = decimal(regra.get("fatorAlerta"), 2d);
            long atual = contar(unidade, texto(regra.get("termos")), LocalDate.now().minusDays(janela - 1), LocalDate.now());
            long base = contar(unidade, texto(regra.get("termos")), LocalDate.now().minusDays(janela * 5L), LocalDate.now().minusDays(janela));
            double media = base / 4d;
            boolean alerta = atual >= minimo && (media == 0 ? atual >= minimo : atual >= media * fator);
            Map<String, Object> item = new LinkedHashMap<>(regra);
            item.put("casosAtuais", atual); item.put("mediaHistorica", media); item.put("fatorObservado", media == 0 ? null : atual / media);
            item.put("nivel", alerta ? (atual >= minimo * 2 ? "ALTO" : "ATENCAO") : "NORMAL");
            item.put("sinalParaInvestigacao", alerta);
            if (alerta) {
                Map<String, Object> ocorrencia = ocorrenciaSistemaService.salvar(ocorrenciaEpidemiologica(unidade, regra, item, atual, media));
                item.put("ocorrenciaId", ocorrencia.get("id"));
            }
            sinais.add(item);
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("geradoEm", LocalDate.now().toString());
        resposta.put("unidade", texto(unidade));
        resposta.put("sinais", sinais);
        carregarPendenciasSinan(resposta, unidade);
        resposta.put("aviso", "Os resultados sao sinais estatisticos e exigem validacao humana pela vigilancia epidemiologica.");
        return Response.ok(resposta).build();
        } catch (Exception e) {
            LOG.error("Falha ao analisar vigilancia epidemiologica", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Vigilancia indisponivel. Confirme a aplicacao das migrations V52 e V53 e consulte o log do backend."))
                    .build();
        }
    }

    /**
     * Retorna somente dados espaciais agregados para o mapa epidemiologico.
     * Nao reutiliza o fluxo legado de marcas.xml e nunca devolve identificadores
     * ou dados cadastrais de pacientes.
     */
    @GET @Path("mapa")
    @Transactional
    public Response mapa(@QueryParam("inicio") String inicio,
                         @QueryParam("fim") String fim,
                         @QueryParam("unidade") String unidade,
                         @QueryParam("origem") String origem,
                         @QueryParam("doenca") String doenca) {
        // Consulta pesada (texto livre de varios campos): mesma consulta em 5 min
        // reaproveita o resultado. Alterar regra limpa o cache (limparCaches).
        String chaveCache = String.join("|", unidadeEfetiva(unidade), texto(inicio), texto(fim),
                texto(origem).toUpperCase(Locale.ROOT), texto(doenca));
        Object[] cache = MAPA_CACHE.get(chaveCache);
        if (cache != null && System.currentTimeMillis() - (Long) cache[0] < MAPA_CACHE_MS) {
            return Response.ok(cache[1]).build();
        }
        Response resposta = mapaInterno(inicio, fim, unidade, origem, doenca, true);
        if (resposta.getStatus() == 200) {
            if (MAPA_CACHE.size() > 500) MAPA_CACHE.clear();
            MAPA_CACHE.put(chaveCache, new Object[] { System.currentTimeMillis(), resposta.getEntity() });
        }
        return resposta;
    }

    // Abaixo disso a doenca nem entra na legenda e nao gera sinal (definido pelo Marcelo).
    private static final int MINIMO_INVESTIGACAO = 20;
    private static final long MAPA_CACHE_MS = 5 * 60 * 1000L;
    private static final Map<String, Object[]> MAPA_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static void limparCaches() {
        MAPA_CACHE.clear();
        ALERTA_CACHE.clear();
    }

    /**
     * completo=false devolve so os pontos (usado para marcar a doenca de cada ponto),
     * sem total nem contagem por doenca, que so a consulta principal precisa.
     */
    private Response mapaInterno(String inicio, String fim, String unidade, String origem, String doenca, boolean completo) {
        try {
            LocalDate dataInicio = LocalDate.parse(texto(inicio));
            LocalDate dataFim = LocalDate.parse(texto(fim));
            if (dataFim.isBefore(dataInicio)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "A data final nao pode ser anterior a data inicial."))
                        .build();
            }
            if (dataInicio.plusDays(92).isBefore(dataFim)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "O intervalo maximo para o mapa epidemiologico e de 93 dias."))
                        .build();
            }
            // Cada unidade ve so o seu mapa: com JWT vale a unidade da sessao, nunca a
            // da query (unidade vazia faria as consultas devolverem todas as unidades).
            unidade = unidadeEfetiva(unidade);
            if (unidade.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "Unidade de trabalho nao identificada. Saia e entre novamente selecionando a unidade no login."))
                        .build();
            }
            garantirEstrutura();
            // Sem filtro, o mapa mostra os casos de todas as doenças monitoradas ativas,
            // e não todos os atendimentos do período (a legenda só lista essas doenças).
            String termosDoenca = texto(doenca);
            if (termosDoenca.isBlank()) {
                List<String> termosAtivos = new ArrayList<>();
                for (Map<String, Object> regra : regrasMapa(unidade)) {
                    if (Boolean.TRUE.equals(regra.get("ativo")) && !texto(regra.get("termos")).isBlank()) {
                        termosAtivos.add(texto(regra.get("termos")));
                    }
                }
                termosDoenca = String.join(",", termosAtivos);
            }
            if (termosDoenca.isBlank()) {
                Map<String, Object> vazio = new LinkedHashMap<>();
                vazio.put("totalCasos", 0);
                vazio.put("pontos", List.of());
                vazio.put("origem", texto(origem).isBlank() ? "GHBAM" : texto(origem));
                vazio.put("doencas", List.of());
                vazio.put("inicio", dataInicio.toString());
                vazio.put("fim", dataFim.toString());
                return Response.ok(vazio).build();
            }

            String sql = """
                    WITH fontes AS (
                        SELECT CAST(p.latitude AS double precision) AS latitude,
                               CAST(p.longitude AS double precision) AS longitude,
                               CAST(b.idbam AS varchar) AS origem_id,
                               NULLIF(UPPER(TRIM(p.bairro)), '') AS bairro,
                               NULLIF(UPPER(TRIM(p.municipio)), '') AS municipio,
                               CAST(b.data AS date) >= :inicio AS atual
                        FROM bam b
                        JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
                        WHERE CAST(b.data AS date) BETWEEN :inicioBase AND :fim
                          AND (:unidade = '' OR b.unidade = :unidade)
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GHBAM')
                          AND (:doenca = '' OR EXISTS (
                              SELECT 1 FROM registro r
                              LEFT JOIN avaliacao a ON a.idavaliacao=b.avaliacao_idavaliacao
                              LEFT JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
                              WHERE r.idregistro=b.registro_idregistro
                                AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(r.queixaprincipal,'') || ' ' || COALESCE(r.obsentrada,'') || ' ' || COALESCE(a.anamnese,'') || ' ' || COALESCE(a.comentario,'') || ' ' || COALESCE(t.diagnostico_provisorio,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ')
                                    ILIKE ANY(CAST(string_to_array(:padroesDoenca, '|') AS text[]))
                          ))
                        UNION ALL
                        SELECT d.latitude, d.longitude,
                               CAST(CONCAT('GPEC-', a.id) AS varchar) AS origem_id,
                               NULLIF(UPPER(TRIM(d.bairro)), '') AS bairro,
                               CAST(NULL AS varchar) AS municipio,
                               a.data_referencia >= :inicio AS atual
                        FROM aps_atendimento a
                        JOIN aps_cidadao c ON c.id = a.id_cidadao
                        LEFT JOIN aps_familia f ON f.id = c.id_familia
                        LEFT JOIN aps_domicilio d ON d.id = f.id_domicilio
                        WHERE a.data_referencia BETWEEN :inicioBase AND :fim
                          AND a.status <> 'CANCELADO'
                          AND (:unidade = '' OR UPPER(a.unidade) = UPPER(:unidade))
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GPEC')
                          AND (:doenca = '' OR EXISTS (
                              SELECT 1 FROM aps_problema pr
                              WHERE pr.id_atendimento = a.id AND pr.tipo = 'PROBLEMA'
                                AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(pr.descricao,'') || ' ' || COALESCE(pr.cid10,'') || ' ' || COALESCE(pr.ciap,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ')
                                    ILIKE ANY(CAST(string_to_array(:padroesDoenca, '|') AS text[]))
                          ))
                    ), concentracoes AS (
                        -- atual = periodo escolhido; o restante sao as 4 janelas anteriores
                        -- de mesmo tamanho, base historica da mesma regiao.
                        SELECT AVG(CAST(latitude AS numeric)) FILTER (WHERE atual) AS latitude,
                               AVG(CAST(longitude AS numeric)) FILTER (WHERE atual) AS longitude,
                               COUNT(DISTINCT origem_id) FILTER (WHERE atual) AS peso,
                               MODE() WITHIN GROUP (ORDER BY bairro) FILTER (WHERE atual) AS bairro,
                               MODE() WITHIN GROUP (ORDER BY municipio) FILTER (WHERE atual) AS municipio,
                               COUNT(DISTINCT origem_id) FILTER (WHERE NOT atual) AS base
                        FROM fontes
                        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
                          AND latitude BETWEEN -90 AND 90
                          AND longitude BETWEEN -180 AND 180
                        GROUP BY ROUND(CAST(latitude AS numeric), 2),
                                 ROUND(CAST(longitude AS numeric), 2)
                        HAVING COUNT(DISTINCT origem_id) FILTER (WHERE atual) >= 3
                    )
                    SELECT latitude, longitude, peso, bairro, municipio, base
                    FROM concentracoes
                    ORDER BY peso DESC
                    """;
            @SuppressWarnings("unchecked")
            long diasPeriodo = java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
            List<Object[]> rows = em.createNativeQuery(sql)
                    .setParameter("inicio", dataInicio)
                    .setParameter("inicioBase", dataInicio.minusDays(diasPeriodo * 4))
                    .setParameter("fim", dataFim)
                    .setParameter("unidade", texto(unidade))
                    .setParameter("origem", texto(origem))
                    .setParameter("doenca", termosDoenca)
                    .setParameter("padroesDoenca", padroesBusca(termosDoenca))
                    .setParameter("acentos", ACENTOS)
                    .setParameter("semAcentos", SEM_ACENTOS)
                    .getResultList();

            String totalSql = """
                    SELECT COUNT(*) FROM (
                        SELECT CAST(b.idbam AS varchar) AS origem_id
                        FROM bam b
                        WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim
                          AND (:unidade = '' OR b.unidade = :unidade)
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GHBAM')
                          AND (:doenca = '' OR EXISTS (
                              SELECT 1 FROM registro r
                              LEFT JOIN avaliacao a ON a.idavaliacao=b.avaliacao_idavaliacao
                              LEFT JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
                              WHERE r.idregistro=b.registro_idregistro
                                AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(r.queixaprincipal,'') || ' ' || COALESCE(r.obsentrada,'') || ' ' || COALESCE(a.anamnese,'') || ' ' || COALESCE(a.comentario,'') || ' ' || COALESCE(t.diagnostico_provisorio,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ')
                                    ILIKE ANY(CAST(string_to_array(:padroesDoenca, '|') AS text[]))
                          ))
                        UNION ALL
                        SELECT CAST(CONCAT('GPEC-', a.id) AS varchar) AS origem_id
                        FROM aps_atendimento a
                        WHERE a.data_referencia BETWEEN :inicio AND :fim
                          AND a.status <> 'CANCELADO'
                          AND (:unidade = '' OR UPPER(a.unidade) = UPPER(:unidade))
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GPEC')
                          AND (:doenca = '' OR EXISTS (
                              SELECT 1 FROM aps_problema pr
                              WHERE pr.id_atendimento = a.id AND pr.tipo = 'PROBLEMA'
                                AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(pr.descricao,'') || ' ' || COALESCE(pr.cid10,'') || ' ' || COALESCE(pr.ciap,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ')
                                    ILIKE ANY(CAST(string_to_array(:padroesDoenca, '|') AS text[]))
                          ))
                    ) fontes
                    """;
            Number total = !completo ? Integer.valueOf(0) : (Number) em.createNativeQuery(totalSql)
                    .setParameter("inicio", dataInicio)
                    .setParameter("fim", dataFim)
                    .setParameter("unidade", texto(unidade))
                    .setParameter("origem", texto(origem))
                    .setParameter("doenca", termosDoenca)
                    .setParameter("padroesDoenca", padroesBusca(termosDoenca))
                    .setParameter("acentos", ACENTOS)
                    .setParameter("semAcentos", SEM_ACENTOS)
                    .getSingleResult();

            List<Map<String, Object>> pontos = new ArrayList<>();
            for (Object[] row : rows) {
                Map<String, Object> ponto = new LinkedHashMap<>();
                ponto.put("latitude", ((Number) row[0]).doubleValue());
                ponto.put("longitude", ((Number) row[1]).doubleValue());
                ponto.put("peso", ((Number) row[2]).longValue());
                // Bairro e município predominantes da região (agregado, nunca do paciente).
                ponto.put("bairro", texto(row[3]));
                ponto.put("municipio", texto(row[4]));
                ponto.put("origem", texto(origem).isBlank() ? "TODOS" : texto(origem));
                // Mesma regra de sinal do analisar(): acima de 2x a media das janelas
                // anteriores; regiao sem historico so sinaliza a partir de 5 casos.
                long peso = ((Number) row[2]).longValue();
                double media = ((Number) row[5]).longValue() / 4d;
                boolean alerta = peso > MINIMO_INVESTIGACAO && peso >= media * 2;
                ponto.put("mediaAnterior", Math.round(media * 10) / 10d);
                ponto.put("sinalParaInvestigacao", alerta);
                ponto.put("nivel", alerta ? (peso > MINIMO_INVESTIGACAO * 2 || (media > 0 && peso >= media * 3) ? "ALTO" : "ATENCAO") : "NORMAL");
                pontos.add(ponto);
            }

            long casosNoMapa = pontos.stream()
                    .mapToLong(p -> ((Number) p.get("peso")).longValue())
                    .sum();
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("totalCasos", total == null ? 0 : total.longValue());
            resposta.put("pontos", pontos);
            resposta.put("casosNoMapa", casosNoMapa);
            resposta.put("origem", texto(origem).isBlank() ? "GHBAM" : texto(origem));
            List<Map<String, Object>> doencas = new ArrayList<>();
            List<Map<String, Object>> regrasAtivas = new ArrayList<>();
            if (completo) {
                for (Map<String, Object> regra : regrasMapa(unidade)) {
                    if (Boolean.TRUE.equals(regra.get("ativo"))) regrasAtivas.add(regra);
                }
            }
            // Uma consulta por fonte conta todas as doencas (antes: 2 consultas por doenca).
            List<String> termosRegras = regrasAtivas.stream().map(regra -> texto(regra.get("termos"))).toList();
            long[] contagemGhbam = "GPEC".equalsIgnoreCase(texto(origem)) ? new long[termosRegras.size()]
                    : contarPorRegra(unidade, termosRegras, dataInicio, dataFim, false);
            long[] contagemGpec = "GHBAM".equalsIgnoreCase(texto(origem)) ? new long[termosRegras.size()]
                    : contarPorRegra(unidade, termosRegras, dataInicio, dataFim, true);
            for (int indiceRegra = 0; indiceRegra < regrasAtivas.size(); indiceRegra++) {
                Map<String, Object> regra = regrasAtivas.get(indiceRegra);
                long casosGhbam = contagemGhbam[indiceRegra];
                long casosGpec = contagemGpec[indiceRegra];
                long casos = "GHBAM".equalsIgnoreCase(texto(origem)) ? casosGhbam
                        : "GPEC".equalsIgnoreCase(texto(origem)) ? casosGpec : casosGhbam + casosGpec;
                if (casos > MINIMO_INVESTIGACAO) {
                    Map<String, Object> opcao = new LinkedHashMap<>();
                    opcao.put("id", regra.get("id"));
                    opcao.put("nome", regra.get("nome"));
                    opcao.put("termos", regra.get("termos"));
                    opcao.put("casos", casos);
                    opcao.put("casosGhbam", casosGhbam);
                    opcao.put("casosGpec", casosGpec);
                    doencas.add(opcao);
                }
            }
            // Quando todas as doenças são consultadas, cada círculo precisa manter
            // a doença que o originou. Reutiliza o mesmo endpoint com o filtro de
            // cada regra, sempre passando a unidade efetiva da sessão/JWT.
            if (completo && texto(doenca).isBlank()) {
                // So as doencas da legenda (acima do minimo) aparecem no mapa.
                List<Map<String, Object>> pontosPorDoenca = new ArrayList<>();
                // A legenda/seletor so lista doenca que tem regiao desenhavel no mapa;
                // escolher uma doenca sem nenhum ponto deixaria o mapa vazio.
                List<Map<String, Object>> doencasNoMapa = new ArrayList<>();
                for (Map<String, Object> doencaMapa : doencas) {
                    String termos = texto(doencaMapa.get("termos"));
                    Response respostaDoenca = mapaInterno(inicio, fim, unidade, origem, termos, false);
                    if (respostaDoenca.getStatus() != 200 || !(respostaDoenca.getEntity() instanceof Map)) continue;
                    Object pontosDaDoenca = ((Map<?, ?>) respostaDoenca.getEntity()).get("pontos");
                    if (!(pontosDaDoenca instanceof List<?>) || ((List<?>) pontosDaDoenca).isEmpty()) continue;
                    doencasNoMapa.add(doencaMapa);
                    for (Object item : (List<?>) pontosDaDoenca) {
                        if (!(item instanceof Map<?, ?>)) continue;
                        Map<String, Object> pontoDoenca = new LinkedHashMap<>();
                        ((Map<?, ?>) item).forEach((chavePonto, valorPonto) -> pontoDoenca.put(String.valueOf(chavePonto), valorPonto));
                        pontoDoenca.put("doenca", texto(doencaMapa.get("nome")));
                        pontosPorDoenca.add(pontoDoenca);
                    }
                }
                doencas = doencasNoMapa;
                resposta.put("pontos", pontosPorDoenca);
                resposta.put("casosNoMapa", pontosPorDoenca.stream().mapToLong(p -> ((Number) p.get("peso")).longValue()).sum());
            } else if (!texto(doenca).isBlank()) {
                String nomeDoenca = regrasMapa(unidade).stream()
                        .filter(regra -> texto(regra.get("termos")).equals(texto(doenca)))
                        .map(regra -> texto(regra.get("nome")))
                        .findFirst()
                        .orElse(texto(doenca));
                pontos.forEach(ponto -> ponto.put("doenca", nomeDoenca));
            }
            resposta.put("doencas", doencas);
            resposta.put("inicio", dataInicio.toString());
            resposta.put("fim", dataFim.toString());
            return Response.ok(resposta).build();
        } catch (java.time.format.DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "As datas devem estar no formato YYYY-MM-DD."))
                    .build();
        } catch (Exception e) {
            LOG.error("Falha ao carregar mapa epidemiologico agregado", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel consultar o mapa epidemiologico."))
                    .build();
        }
    }

    /**
     * Aumentos fora da lista monitorada: agrupa os diagnosticos do periodo (texto do
     * diagnostico provisorio no GHBAM; CID-10/CIAP do problema do atendimento no G-PEC)
     * e devolve os que passaram de 2x a media das 4 janelas anteriores. So agregados.
     */
    @GET @Path("emergentes")
    @Transactional
    public Response emergentes(@QueryParam("inicio") String inicio,
                               @QueryParam("fim") String fim,
                               @QueryParam("unidade") String unidade,
                               @QueryParam("origem") String origem) {
        try {
            LocalDate dataInicio = LocalDate.parse(texto(inicio));
            LocalDate dataFim = LocalDate.parse(texto(fim));
            if (dataFim.isBefore(dataInicio) || dataInicio.plusDays(92).isBefore(dataFim)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "Informe um intervalo de ate 93 dias.")).build();
            }
            unidade = unidadeEfetiva(unidade);
            if (unidade.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "Unidade de trabalho nao identificada. Saia e entre novamente selecionando a unidade no login."))
                        .build();
            }
            garantirEstrutura();
            long dias = java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
            String sql = """
                    WITH fontes AS (
                        SELECT 'GHBAM' AS fonte,
                               LEFT(TRIM(REGEXP_REPLACE(TRANSLATE(LOWER(t.diagnostico_provisorio), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g')), 80) AS chave,
                               CAST(NULL AS varchar) AS descricao,
                               CAST(b.data AS date) >= :inicio AS atual,
                               CAST(b.idbam AS varchar) AS origem_id
                        FROM bam b
                        JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                        WHERE CAST(b.data AS date) BETWEEN :inicioBase AND :fim
                          AND b.unidade = :unidade
                          AND COALESCE(TRIM(t.diagnostico_provisorio), '') <> ''
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GHBAM')
                        UNION ALL
                        SELECT 'GPEC',
                               UPPER(COALESCE(NULLIF(TRIM(pr.cid10), ''), NULLIF(TRIM(pr.ciap), ''),
                                     LEFT(TRIM(TRANSLATE(LOWER(pr.descricao), :acentos, :semAcentos)), 80))),
                               pr.descricao,
                               a.data_referencia >= :inicio,
                               CAST(CONCAT('GPEC-', a.id) AS varchar)
                        FROM aps_atendimento a
                        JOIN aps_problema pr ON pr.id_atendimento = a.id AND pr.tipo = 'PROBLEMA'
                        WHERE a.data_referencia BETWEEN :inicioBase AND :fim
                          AND a.status <> 'CANCELADO'
                          AND UPPER(a.unidade) = UPPER(:unidade)
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GPEC')
                    )
                    SELECT fonte, chave, MODE() WITHIN GROUP (ORDER BY descricao) AS descricao,
                           COUNT(DISTINCT origem_id) FILTER (WHERE atual) AS casos,
                           COUNT(DISTINCT origem_id) FILTER (WHERE NOT atual) AS base
                    FROM fontes
                    WHERE COALESCE(chave, '') <> ''
                    GROUP BY fonte, chave
                    HAVING COUNT(DISTINCT origem_id) FILTER (WHERE atual) > 20 -- MINIMO_INVESTIGACAO
                    ORDER BY casos DESC
                    LIMIT 200
                    """;
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(sql)
                    .setParameter("inicio", dataInicio)
                    .setParameter("inicioBase", dataInicio.minusDays(dias * 4))
                    .setParameter("fim", dataFim)
                    .setParameter("unidade", unidade)
                    .setParameter("origem", texto(origem))
                    .setParameter("acentos", ACENTOS)
                    .setParameter("semAcentos", SEM_ACENTOS)
                    .getResultList();

            List<String> termosMonitorados = new ArrayList<>();
            for (Map<String, Object> regra : regrasMapa(unidade)) {
                for (String termo : normalizarBusca(texto(regra.get("termos"))).split(",")) {
                    if (!termo.isBlank()) termosMonitorados.add(termo.trim());
                }
            }
            List<Map<String, Object>> itens = new ArrayList<>();
            for (Object[] row : rows) {
                long casos = ((Number) row[3]).longValue();
                double media = ((Number) row[4]).longValue() / 4d;
                // Mesma regra de sinal do analisar(): 2x a media; sem historico, 5 casos.
                if (casos < media * 2) continue;
                String fonte = texto(row[0]);
                String chave = texto(row[1]);
                String descricao = texto(row[2]);
                String rotulo = "GPEC".equals(fonte) && !descricao.isBlank() && !descricao.equalsIgnoreCase(chave)
                        ? chave + " - " + descricao : chave;
                String termos = "GPEC".equals(fonte) && !descricao.isBlank() && !descricao.equalsIgnoreCase(chave)
                        ? normalizarBusca(chave) + ", " + normalizarBusca(descricao) : normalizarBusca(chave);
                String alvo = normalizarBusca(rotulo);
                boolean monitorado = termosMonitorados.stream().anyMatch(alvo::contains);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fonte", fonte);
                item.put("rotulo", rotulo);
                item.put("termosSugeridos", termos);
                item.put("casos", casos);
                item.put("mediaAnterior", Math.round(media * 10) / 10d);
                item.put("nivel", casos > MINIMO_INVESTIGACAO * 2 || (media > 0 && casos >= media * 3) ? "ALTO" : "ATENCAO");
                item.put("monitorado", monitorado);
                itens.add(item);
            }
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("itens", itens);
            resposta.put("aviso", "Aumento estatistico por agrupamento de texto/CID; exige validacao humana e nao confirma surto.");
            return Response.ok(resposta).build();
        } catch (java.time.format.DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "As datas devem estar no formato YYYY-MM-DD.")).build();
        } catch (Exception e) {
            LOG.error("Falha ao detectar aumentos epidemiologicos", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel analisar os aumentos de casos.")).build();
        }
    }

    private static final long ALERTA_CACHE_MS = 10 * 60 * 1000L;
    private static final Map<String, Object[]> ALERTA_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Resumo para o alerta do cabecalho (medico/enfermagem): regioes e diagnosticos
     * acima do esperado nos ultimos 7 dias da unidade. Cache de 10 min por unidade,
     * pois todos os profissionais consultam periodicamente.
     */
    @GET @Path("alerta")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response alerta(@QueryParam("unidade") String unidade) {
        String chave = unidadeEfetiva(unidade);
        if (chave.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Unidade de trabalho nao identificada.")).build();
        }
        Object[] cache = ALERTA_CACHE.get(chave);
        if (cache != null && System.currentTimeMillis() - (Long) cache[0] < ALERTA_CACHE_MS) {
            return Response.ok(cache[1]).build();
        }
        String inicio = LocalDate.now().minusDays(6).toString();
        String fim = LocalDate.now().toString();
        Response mapa = mapa(inicio, fim, chave, "TODOS", "");
        Response emergentes = emergentes(inicio, fim, chave, "TODOS");
        if (mapa.getStatus() != 200 || emergentes.getStatus() != 200) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel calcular o alerta epidemiologico.")).build();
        }
        long regioes = ((List<Map<String, Object>>) ((Map<String, Object>) mapa.getEntity()).get("pontos")).stream()
                .filter(p -> Boolean.TRUE.equals(p.get("sinalParaInvestigacao")))
                .map(p -> texto(p.get("latitude")) + ":" + texto(p.get("longitude")))
                .distinct()
                .count();
        List<Map<String, Object>> itens = (List<Map<String, Object>>) ((Map<String, Object>) emergentes.getEntity()).get("itens");
        boolean alto = itens.stream().anyMatch(i -> "ALTO".equals(i.get("nivel")));
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("regioes", regioes);
        resposta.put("diagnosticos", itens.size());
        resposta.put("total", regioes + itens.size());
        resposta.put("nivel", alto ? "ALTO" : (regioes + itens.size() > 0 ? "ATENCAO" : "NORMAL"));
        resposta.put("inicio", inicio);
        resposta.put("fim", fim);
        ALERTA_CACHE.put(chave, new Object[] { System.currentTimeMillis(), resposta });
        return Response.ok(resposta).build();
    }

    /** Com JWT vale a unidade da sessao, nunca a informada; sem JWT (dev legado), a informada. */
    private String unidadeEfetiva(String informada) {
        String sessao = securityPermissionService.unidadeDaSessao(requestContext);
        String valor = sessao.isBlank() ? texto(informada) : sessao;
        return valor.split(":")[0].trim();
    }

    @GET @Path("configuracao")
    @Transactional
    public Response configuracao(@QueryParam("unidade") String unidade) {
        try {
            garantirEstrutura();
            return Response.ok(regras(unidade)).build();
        } catch (Exception e) {
            LOG.error("Falha ao carregar configuracao da vigilancia epidemiologica", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel preparar a estrutura da vigilancia epidemiologica. Consulte o log do backend."))
                    .build();
        }
    }

    @GET @Path("configuracao/{id}/casos")
    @Transactional
    public Response casos(@PathParam("id") int id) {
        garantirEstrutura();
        List<Map<String, Object>> regra = regras("").stream().filter(item -> numero(item.get("id"), 0) == id).toList();
        if (regra.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
        Map<String, Object> r = regra.get(0); int janela = numero(r.get("janelaDias"), 7);
        String sql = """
                SELECT DISTINCT ON (b.idbam)
                       b.idbam,p.idpaciente,p.nome,b.data,b.unidade,r.setor,r.queixaprincipal,t.diagnostico_provisorio,
                       a.anamnese,a.comentario,a.hpp,t.evolucao_prescricao,t.obs_enfermagem,ai.frente,ai.verso
                FROM bam b
                JOIN pacientes p ON p.idpaciente=b.paciente_idpaciente
                JOIN registro r ON r.idregistro=b.registro_idregistro
                LEFT JOIN avaliacao a ON a.idavaliacao=b.avaliacao_idavaliacao
                LEFT JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
                LEFT JOIN anamneseinternacao ai ON ai.nbam=b.idbam
                WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim
                  AND (:unidade='' OR b.unidade=:unidade)
                  AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(
                    COALESCE(r.queixaprincipal,'') || ' ' ||
                    COALESCE(r.obsentrada,'') || ' ' ||
                    COALESCE(a.anamnese,'') || ' ' ||
                    COALESCE(a.comentario,'') || ' ' ||
                    COALESCE(a.hpp,'') || ' ' ||
                    COALESCE(a.risco,'') || ' ' ||
                    COALESCE(t.diagnostico_provisorio,'') || ' ' ||
                    COALESCE(t.evolucao_prescricao,'') || ' ' ||
                    COALESCE(t.obs_enfermagem,'') || ' ' ||
                    COALESCE(ai.frente,'') || ' ' ||
                    COALESCE(ai.verso,'')
                  ), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ') ILIKE ANY(CAST(string_to_array(:padroes,'|') AS text[]))
                ORDER BY b.idbam, b.data DESC
                LIMIT 500
                """;
        String padroes = padroesBusca(texto(r.get("termos")));
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("inicio", LocalDate.now().minusDays(janela - 1))
                .setParameter("fim", LocalDate.now())
                .setParameter("unidade", texto(r.get("unidade")))
                .setParameter("acentos", ACENTOS)
                .setParameter("semAcentos", SEM_ACENTOS)
                .setParameter("padroes", padroes)
                .getResultList();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object[] x : rows) {
            Map<String,Object> m = new LinkedHashMap<>();
            String[] k={"bam","idpaciente","paciente","data","unidade","setor","queixaPrincipal","diagnostico","anamneseTriagem","comentarioTriagem","hpp","evolucaoMedica","observacaoEnfermagem","anamneseMedicaFrente","anamneseMedicaVerso"};
            for(int i=0;i<k.length;i++)m.put(k[i],x[i]);
            out.add(m);
        }
        return Response.ok(out).build();
    }

    @POST @Path("configuracao") @Transactional
    public Response criar(Map<String, Object> dto) {
        garantirEstrutura(); validar(dto); limparCaches(); String unidade = unidadeEfetiva(texto(dto.get("unidade")));
        em.createNativeQuery("INSERT INTO vigilancia_epidemiologica_regra (unidade,nome,termos,ativo,janela_dias,minimo_casos,fator_alerta,responsavel) VALUES (:u,:n,:t,:a,:j,:m,:f,:r)")
          .setParameter("u", unidade).setParameter("n", texto(dto.get("nome"))).setParameter("t", texto(dto.get("termos"))).setParameter("a", booleano(dto.get("ativo"), true)).setParameter("j", numero(dto.get("janelaDias"), 7)).setParameter("m", numero(dto.get("minimoCasos"), 3)).setParameter("f", decimal(dto.get("fatorAlerta"), 2)).setParameter("r", texto(dto.get("responsavel"))).executeUpdate();
        return Response.status(Response.Status.CREATED).build();
    }

    @PUT @Path("configuracao/{id}") @Transactional
    public Response atualizar(@PathParam("id") int id, Map<String, Object> dto) {
        garantirEstrutura(); validar(dto); limparCaches();
        int total = em.createNativeQuery("UPDATE vigilancia_epidemiologica_regra SET nome=:n,termos=:t,ativo=:a,janela_dias=:j,minimo_casos=:m,fator_alerta=:f,responsavel=:r,atualizado_em=now() WHERE id=:id")
          .setParameter("id", id).setParameter("n", texto(dto.get("nome"))).setParameter("t", texto(dto.get("termos"))).setParameter("a", booleano(dto.get("ativo"), true)).setParameter("j", numero(dto.get("janelaDias"), 7)).setParameter("m", numero(dto.get("minimoCasos"), 3)).setParameter("f", decimal(dto.get("fatorAlerta"), 2)).setParameter("r", texto(dto.get("responsavel"))).executeUpdate();
        return total == 0 ? Response.status(Response.Status.NOT_FOUND).build() : Response.noContent().build();
    }

    @POST @Path("ocorrencia/{id}/investigacao")
    public Response registrarInvestigacao(@PathParam("id") int id, Map<String, Object> dto) {
        String acao = texto(dto == null ? null : dto.get("acao")).toLowerCase();
        String justificativa = texto(dto == null ? null : dto.get("justificativa"));
        String medida = texto(dto == null ? null : dto.get("medida"));
        if (!List.of("assumir", "confirmar", "descartar", "encerrar").contains(acao)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Acao de investigacao invalida.")).build();
        }
        if (!"assumir".equals(acao) && (justificativa.isBlank() || medida.isBlank())) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Justificativa e medida adotada sao obrigatorias.")).build();
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("usuario", texto(dto == null ? null : dto.get("usuario")));
        patch.put("observacao", "Investigacao " + acao + ". Justificativa: " + justificativa + ". Medida: " + medida);
        if ("assumir".equals(acao)) { patch.put("status", "analise"); patch.put("responsavel", texto(dto == null ? null : dto.get("responsavel"))); }
        if ("confirmar".equals(acao) || "descartar".equals(acao)) patch.put("status", "analise");
        if ("encerrar".equals(acao)) patch.put("status", "resolvida");
        return Response.ok(ocorrenciaSistemaService.atualizar(id, patch)).build();
    }

    @POST @Path("sinan/{id}/fluxo")
    public Response atualizarFluxoSinan(@PathParam("id") Long id, Map<String, Object> dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Dados da acao SINAN sao obrigatorios.")).build();
        }
        try {
            NotificacaoSinan item = notificacaoSinanService.avancarFluxo(
                    id,
                    texto(dto.get("acao")),
                    texto(dto.get("usuario")),
                    texto(dto.get("referencia")),
                    texto(dto.get("observacao"))
            );
            if (item == null) {
                return Response.status(Response.Status.NOT_FOUND).entity(Map.of("erro", "Ficha SINAN nao encontrada.")).build();
            }
            return Response.ok(item).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("erro", e.getMessage())).build();
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao atualizar fluxo SINAN. notificacaoId=%s, acao=%s", id, texto(dto.get("acao")));
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "O fluxo SINAN esta temporariamente indisponivel. Tente novamente; se persistir, consulte o log do backend."))
                    .build();
        }
    }

    @DELETE @Path("configuracao/{id}") @Transactional
    public Response excluir(@PathParam("id") int id) {
        garantirEstrutura(); limparCaches();
        int total = em.createNativeQuery("DELETE FROM vigilancia_epidemiologica_regra WHERE id=:id")
                .setParameter("id", id).executeUpdate();
        return total == 0 ? Response.status(Response.Status.NOT_FOUND).build() : Response.noContent().build();
    }

    /**
     * Mesmos campos e criterios de contar()/contarGpec(), mas para varias regras numa
     * unica consulta: o texto de cada atendimento e montado uma vez e testado contra
     * os padroes de cada regra. Devolve os casos na ordem de termosRegras.
     */
    private long[] contarPorRegra(String unidade, List<String> termosRegras, LocalDate inicio, LocalDate fim, boolean gpec) {
        long[] contagem = new long[termosRegras.size()];
        if (termosRegras.isEmpty()) return contagem;
        String lista = String.join(";;", termosRegras.stream().map(this::padroesBusca).toList());
        String regrasCte = """
                WITH regras AS (
                    SELECT CAST(ord AS int) AS idx, string_to_array(padroes, '|') AS padroes
                    FROM unnest(string_to_array(:lista, ';;')) WITH ORDINALITY AS x(padroes, ord)
                    WHERE padroes <> ''
                ), textos AS (
                """;
        String textos = gpec ? """
                    SELECT a.id AS origem_id,
                           ' ' || REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(pr.descricao,'') || ' ' || COALESCE(pr.cid10,'') || ' ' || COALESCE(pr.ciap,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ' AS txt
                    FROM aps_atendimento a
                    JOIN aps_problema pr ON pr.id_atendimento = a.id AND pr.tipo = 'PROBLEMA'
                    WHERE a.data_referencia BETWEEN :inicio AND :fim
                      AND a.status <> 'CANCELADO'
                      AND (:unidade='' OR UPPER(a.unidade)=UPPER(:unidade))
                """ : """
                    SELECT b.idbam AS origem_id,
                           ' ' || REGEXP_REPLACE(TRANSLATE(LOWER(
                             COALESCE(r.queixaprincipal,'') || ' ' || COALESCE(r.obsentrada,'') || ' ' ||
                             COALESCE(a.anamnese,'') || ' ' || COALESCE(a.comentario,'') || ' ' ||
                             COALESCE(a.hpp,'') || ' ' || COALESCE(a.risco,'') || ' ' ||
                             COALESCE(t.diagnostico_provisorio,'') || ' ' || COALESCE(t.evolucao_prescricao,'') || ' ' ||
                             COALESCE(t.obs_enfermagem,'') || ' ' || COALESCE(ai.frente,'') || ' ' || COALESCE(ai.verso,'')
                           ), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ' AS txt
                    FROM bam b
                    JOIN registro r ON r.idregistro=b.registro_idregistro
                    LEFT JOIN avaliacao a ON a.idavaliacao=b.avaliacao_idavaliacao
                    LEFT JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
                    LEFT JOIN anamneseinternacao ai ON ai.nbam=b.idbam
                    WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim
                      AND (:unidade='' OR b.unidade=:unidade)
                """;
        String sql = regrasCte + textos + """
                )
                SELECT g.idx, COUNT(DISTINCT t.origem_id)
                FROM regras g
                JOIN textos t ON t.txt ILIKE ANY(g.padroes)
                GROUP BY g.idx
                """;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("lista", lista)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("unidade", texto(unidade))
                .setParameter("acentos", ACENTOS)
                .setParameter("semAcentos", SEM_ACENTOS)
                .getResultList();
        for (Object[] row : rows) {
            int idx = ((Number) row[0]).intValue() - 1;
            if (idx >= 0 && idx < contagem.length) contagem[idx] = ((Number) row[1]).longValue();
        }
        return contagem;
    }

    private long contar(String unidade, String termos, LocalDate inicio, LocalDate fim) {
        if (termos.isBlank()) return 0;
        String sql = """
                SELECT count(DISTINCT b.idbam)
                FROM bam b
                JOIN registro r ON r.idregistro=b.registro_idregistro
                LEFT JOIN avaliacao a ON a.idavaliacao=b.avaliacao_idavaliacao
                LEFT JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
                LEFT JOIN anamneseinternacao ai ON ai.nbam=b.idbam
                WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim
                  AND (:unidade='' OR b.unidade=:unidade)
                  AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(
                    COALESCE(r.queixaprincipal,'') || ' ' ||
                    COALESCE(r.obsentrada,'') || ' ' ||
                    COALESCE(a.anamnese,'') || ' ' ||
                    COALESCE(a.comentario,'') || ' ' ||
                    COALESCE(a.hpp,'') || ' ' ||
                    COALESCE(a.risco,'') || ' ' ||
                    COALESCE(t.diagnostico_provisorio,'') || ' ' ||
                    COALESCE(t.evolucao_prescricao,'') || ' ' ||
                    COALESCE(t.obs_enfermagem,'') || ' ' ||
                    COALESCE(ai.frente,'') || ' ' ||
                    COALESCE(ai.verso,'')
                  ), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ') ILIKE ANY(CAST(string_to_array(:padroes, '|') AS text[]))
                """;
        Query q = em.createNativeQuery(sql)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("unidade", texto(unidade))
                .setParameter("acentos", ACENTOS)
                .setParameter("semAcentos", SEM_ACENTOS)
                .setParameter("padroes", padroesBusca(termos));
        return ((Number) q.getSingleResult()).longValue();
    }

    private long contarGpec(String unidade, String termos, LocalDate inicio, LocalDate fim) {
        if (termos.isBlank()) return 0;
        String sql = """
                SELECT count(DISTINCT a.id)
                FROM aps_atendimento a
                JOIN aps_problema pr ON pr.id_atendimento = a.id AND pr.tipo = 'PROBLEMA'
                WHERE a.data_referencia BETWEEN :inicio AND :fim
                  AND a.status <> 'CANCELADO'
                  AND (:unidade='' OR UPPER(a.unidade)=UPPER(:unidade))
                  AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(pr.descricao,'') || ' ' || COALESCE(pr.cid10,'') || ' ' || COALESCE(pr.ciap,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ')
                      ILIKE ANY(CAST(string_to_array(:padroes, '|') AS text[]))
                """;
        return ((Number) em.createNativeQuery(sql)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("unidade", texto(unidade))
                .setParameter("acentos", ACENTOS)
                .setParameter("semAcentos", SEM_ACENTOS)
                .setParameter("padroes", padroesBusca(termos))
                .getSingleResult()).longValue();
    }

    private void carregarPendenciasSinan(Map<String, Object> resposta, String unidade) {
        try {
            resposta.put("pendenciasSinan", notificacaoSinanService.listarPendenciasPorUnidade(unidade));
            resposta.put("pendenciasSinanDisponivel", true);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao consultar pendencias SINAN. unidade=%s", texto(unidade));
            resposta.put("pendenciasSinan", List.of());
            resposta.put("pendenciasSinanDisponivel", false);
            resposta.put("erroPendenciasSinan", "Nao foi possivel carregar a fila SINAN. Atualize a tela; se persistir, consulte o log do backend.");
        }
    }

    private static volatile boolean estruturaPronta = false;
    private static final Object LOCK_ESTRUTURA = new Object();

    private void garantirEstrutura() {
        if (estruturaPronta) return;
        synchronized (LOCK_ESTRUTURA) {
            if (estruturaPronta) return;
            em.createNativeQuery("""
                    CREATE TABLE IF NOT EXISTS vigilancia_epidemiologica_regra (
                        id SERIAL PRIMARY KEY, unidade VARCHAR(30) NOT NULL DEFAULT '', nome VARCHAR(120) NOT NULL,
                        termos TEXT NOT NULL, ativo BOOLEAN NOT NULL DEFAULT TRUE, janela_dias INTEGER NOT NULL DEFAULT 7,
                        minimo_casos INTEGER NOT NULL DEFAULT 3, fator_alerta NUMERIC(8,2) NOT NULL DEFAULT 2,
                        responsavel VARCHAR(500) NOT NULL DEFAULT '', criado_em TIMESTAMP NOT NULL DEFAULT now(),
                        atualizado_em TIMESTAMP NOT NULL DEFAULT now()
                    )
                    """).executeUpdate();
            em.createNativeQuery("CREATE INDEX IF NOT EXISTS idx_vigilancia_regra_unidade ON vigilancia_epidemiologica_regra (unidade, ativo)").executeUpdate();
            estruturaPronta = true;
        }
    }
    private Map<String, Object> ocorrenciaEpidemiologica(String unidade, Map<String, Object> regra, Map<String, Object> item, long atual, double media) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("unidade", texto(unidade)); dto.put("tipo", "epidemiologia"); dto.put("gravidade", "ALTO".equals(item.get("nivel")) ? "alta" : "media");
        dto.put("status", "aberta"); dto.put("modulo", "Vigilancia epidemiologica"); dto.put("responsavel", texto(regra.get("responsavel")));
        dto.put("origem", "Automatica"); dto.put("criadoPor", "Sistema"); dto.put("path", "/base2/vigilancia-epidemiologica");
        dto.put("titulo", "Sinal epidemiologico: " + texto(regra.get("nome")));
        dto.put("descricao", "Casos no periodo: " + atual + ". Media historica: " + media + ". Validacao humana obrigatoria.");
        return dto;
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> regras(String unidade) {
        List<Object[]> rows=em.createNativeQuery("SELECT id,unidade,nome,termos,ativo,janela_dias,minimo_casos,fator_alerta,responsavel FROM vigilancia_epidemiologica_regra WHERE :u='' OR unidade=:u ORDER BY nome").setParameter("u",texto(unidade)).getResultList(); List<Map<String,Object>> out=new ArrayList<>(); for(Object[] r:rows){Map<String,Object> x=new LinkedHashMap<>(); String[] k={"id","unidade","nome","termos","ativo","janelaDias","minimoCasos","fatorAlerta","responsavel"}; for(int i=0;i<k.length;i++)x.put(k[i],r[i]);out.add(x);}return out;
    }
    /**
     * Agravos/sindromes vigiados no mapa mesmo sem regra cadastrada na unidade
     * (base: notificacao compulsoria e sindromes de surto). Busca por texto:
     * gera sinal para investigacao, nunca diagnostico.
     */
    private static final String[][] CATALOGO_PADRAO = {
        {"Arboviroses (dengue, chikungunya, zika)", "dengue, chikungunya, chicungunha, zika, arbovirose"},
        {"Síndrome gripal / influenza", "sindrome gripal, influenza, gripe"},
        {"SRAG", "srag, sindrome respiratoria aguda grave"},
        {"COVID-19", "covid, sars-cov-2, coronavirus"},
        {"Doença diarreica aguda", "diarreia, diarreica, gastroenterite, gastroenterocolite"},
        {"Doença transmitida por alimento", "intoxicacao alimentar, toxinfeccao alimentar"},
        {"Leptospirose", "leptospirose"},
        {"Hepatites", "hepatite"},
        {"Sarampo", "sarampo"},
        {"Rubéola", "rubeola"},
        {"Varicela", "varicela, catapora"},
        {"Caxumba", "caxumba, parotidite"},
        {"Coqueluche", "coqueluche"},
        {"Meningite", "meningite, meningococ"},
        {"Tuberculose", "tuberculose"},
        {"Conjuntivite", "conjuntivite"},
        {"Escabiose", "escabiose, sarna"},
        {"Doença mão-pé-boca", "mao-pe-boca, mao pe boca"},
        {"Escarlatina", "escarlatina"},
        {"Mpox", "mpox, monkeypox"},
        {"Febre amarela", "febre amarela"},
        {"Malária", "malaria"},
        {"Sífilis", "sifilis"},
        {"Atendimento antirrábico", "mordedura, antirrabic, agressao por animal"},
    };

    /**
     * Doencas do mapa: as regras ativas da unidade mais o catalogo padrao, sem repetir
     * termo ja coberto por regra da unidade. Os casos continuam filtrados pela unidade.
     */
    private List<Map<String,Object>> regrasMapa(String unidade) {
        List<Map<String,Object>> out = new ArrayList<>(regras(unidade));
        java.util.Set<String> termosUsados = new java.util.HashSet<>();
        for (Map<String,Object> regra : out) {
            for (String termo : normalizarBusca(texto(regra.get("termos"))).split(",")) termosUsados.add(termo.trim());
        }
        for (int i = 0; i < CATALOGO_PADRAO.length; i++) {
            String termos = CATALOGO_PADRAO[i][1];
            boolean coberto = false;
            for (String termo : termos.split(",")) coberto |= termosUsados.contains(termo.trim());
            if (coberto) continue;
            Map<String,Object> regra = new LinkedHashMap<>();
            regra.put("id", "padrao-" + i);
            regra.put("unidade", "");
            regra.put("nome", CATALOGO_PADRAO[i][0]);
            regra.put("termos", termos);
            regra.put("ativo", true);
            out.add(regra);
        }
        return out;
    }
    private void validar(Map<String,Object> d){if(d==null||texto(d.get("nome")).isBlank()||texto(d.get("termos")).isBlank())throw new jakarta.ws.rs.BadRequestException("Nome e termos sao obrigatorios.");}
    private String normalizarBusca(String valor) {
        String semAcento = Normalizer.normalize(texto(valor), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).trim();
    }
    private String padroesBusca(String termos) {
        String[] partes = normalizarBusca(termos).split(",");
        List<String> padroes = new ArrayList<>();
        for (String parte : partes) {
            String termo = parte.trim();
            if (!termo.isBlank()) {
                String normalizado = termo.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
                if (!normalizado.isBlank()) padroes.add("% " + normalizado + " %");
            }
        }
        return String.join("|", padroes);
    }
    private String texto(Object v){return v==null?"":String.valueOf(v).trim();} private int numero(Object v,int p){try{return Math.max(1,Integer.parseInt(texto(v)));}catch(Exception e){return p;}} private double decimal(Object v,double p){try{return Math.max(1d,Double.parseDouble(texto(v)));}catch(Exception e){return p;}} private boolean booleano(Object v,boolean p){return v==null?p:Boolean.parseBoolean(texto(v));}
}
