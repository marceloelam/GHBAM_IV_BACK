package br.com.iv.controller;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
import br.com.iv.service.AvaliacaoSinalEpidemiologico;
import br.com.iv.service.ClassificacaoCasoEpidemiologico;
import br.com.iv.service.DefinicaoCasoEpidemiologico;
import br.com.iv.service.EvidenciaCasoEpidemiologico;
import br.com.iv.service.GeocodificacaoPacienteService;
import br.com.iv.service.NotificacaoSinanService;
import br.com.iv.service.OcorrenciaSistemaService;
import br.com.iv.service.SindromeEpidemiologica;

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
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "ghbam.vigilancia.laudo-pdf.sugestoes", defaultValue = "false")
    boolean sugestoesLaudoPdf;

    @GET
    @Transactional
    public Response analisar(@QueryParam("unidade") String unidadeInformada) {
        try {
        garantirEstrutura();
        String unidade = unidadeEfetiva(unidadeInformada);
        // A varredura de texto custava ~13 s por abertura; o resultado vale por 10 min e a fila SINAN segue sempre atual.
        String chaveCache = LocalDate.now() + "|" + texto(unidade);
        Object[] cache = ANALISE_CACHE.get(chaveCache);
        if (cache != null && System.currentTimeMillis() - (Long) cache[0] < ALERTA_CACHE_MS) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resposta = new LinkedHashMap<>((Map<String, Object>) cache[1]);
            carregarPendenciasSinan(resposta, unidade);
            return Response.ok(resposta).build();
        }
        List<Map<String, Object>> regras = regras(unidade).stream().filter(r -> Boolean.TRUE.equals(r.get("ativo"))).toList();
        Map<Map<String, Object>, ContagemRegra> contagens = contarPorJanela(unidade, regras);
        List<Map<String, Object>> sinais = new ArrayList<>();
        for (Map<String, Object> regra : regras) {
            ContagemRegra contagem = contagens.get(regra);
            LimitesRegra limites = limitesDe(regra);
            long atual = contagem.atual();
            double media = contagem.base() / 4d;
            Map<String, Object> item = new LinkedHashMap<>(regra);
            Map<String, Object> chaveOcorrencia = ocorrenciaEpidemiologica(unidade, regra, item, atual, media);
            Optional<Map<String, Object>> aberta = ocorrenciaSistemaService.automaticaAberta(chaveOcorrencia);
            boolean revisado = aberta.isEmpty() && ocorrenciaSistemaService.epidemiologicaEncerradaDesde(chaveOcorrencia,
                    LocalDate.now().minusDays(numero(regra.get("janelaDias"), 7) - 1L).atStartOfDay());
            AvaliacaoSinalEpidemiologico.Resultado avaliacao = AvaliacaoSinalEpidemiologico.avaliar(new AvaliacaoSinalEpidemiologico.Entrada(
                    atual, media, contagem.mediaEpoca(), contagem.ajusteRecente(), contagem.ajusteEpoca(), limites.minimo(), limites.fator(),
                    DefinicaoCasoEpidemiologico.casoUnicoImporta(texto(regra.get("nome")), texto(regra.get("termos"))), aberta.isPresent(), revisado));
            boolean alerta = avaliacao.alerta();
            item.put("casosAtuais", atual); item.put("mediaHistorica", media); item.put("fatorObservado", media == 0 ? null : atual / media);
            item.put("casosMencaoNegada", contagem.negadas());
            item.put("casosSemCriterio", contagem.semCriterio());
            item.put("casosConfirmados", contagem.confirmados());
            item.put("casosProvaveis", contagem.provaveis());
            item.put("casosDescartados", contagem.descartados());
            item.put("esperadoRecente", avaliacao.esperadoRecente());
            item.put("esperadoEpoca", avaliacao.esperadoEpoca());
            item.put("compativelComEpoca", avaliacao.compativelComEpoca());
            item.put("estado", avaliacao.estado().name());
            item.put("motivo", motivoDoSinal(avaliacao, aberta.isPresent()));
            item.put("nivel", alerta ? (atual >= limites.pesoAlto() && !avaliacao.compativelComEpoca() ? "ALTO" : "ATENCAO") : "NORMAL");
            item.put("sinalParaInvestigacao", alerta);
            if (avaliacao.registrarOcorrencia()) {
                Map<String, Object> dto = ocorrenciaEpidemiologica(unidade, regra, item, atual, media);
                // Quem ja assumiu a ocorrencia nao pode voltar a "aberta" nem perder a responsabilidade a cada analise.
                aberta.ifPresent(atualAberta -> { dto.put("status", atualAberta.get("status")); dto.put("responsavel", atualAberta.get("responsavel")); });
                item.put("ocorrenciaId", ocorrenciaSistemaService.salvar(dto).get("id"));
            } else {
                aberta.ifPresent(atualAberta -> item.put("ocorrenciaId", atualAberta.get("id")));
            }
            sinais.add(item);
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("geradoEm", LocalDate.now().toString());
        resposta.put("unidade", texto(unidade));
        resposta.put("sinais", sinais);
        resposta.put("aviso", "Os resultados sao sinais estatisticos e exigem validacao humana pela vigilancia epidemiologica.");
        ANALISE_CACHE.put(chaveCache, new Object[] { System.currentTimeMillis(), new LinkedHashMap<>(resposta) });
        carregarPendenciasSinan(resposta, unidade);
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
        // A chave nao leva a doenca: a varredura classifica todas, e trocar o filtro nao consulta o banco de novo.
        String chaveCache = String.join("|", unidadeEfetiva(unidade), texto(inicio), texto(fim),
                texto(origem).toUpperCase(Locale.ROOT));
        Response completo = mapaCompleto(chaveCache, inicio, fim, unidade, origem);
        if (texto(doenca).isBlank() || completo.getStatus() != 200) return completo;
        String nomeDoenca = regrasMapa(unidadeEfetiva(unidade)).stream()
                .filter(regra -> Boolean.TRUE.equals(regra.get("ativo")) && texto(regra.get("termos")).equals(texto(doenca)))
                .map(regra -> texto(regra.get("nome")))
                .findFirst().orElse(null);
        // Termo fora das regras ativas nao esta na varredura: segue pela consulta propria.
        if (nomeDoenca == null) return mapaInterno(inicio, fim, unidade, origem, doenca, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> dados = (Map<String, Object>) completo.getEntity();
        return Response.ok(filtrarPorDoenca(dados, nomeDoenca)).build();
    }

    private Response mapaCompleto(String chaveCache, String inicio, String fim, String unidade, String origem) {
        Object[] cache = MAPA_CACHE.get(chaveCache);
        if (cache != null && System.currentTimeMillis() - (Long) cache[0] < MAPA_CACHE_MS) {
            return Response.ok(cache[1]).build();
        }
        // Consulta igual ja em andamento (tela + alerta do cabecalho): espera e reaproveita, sem dobrar a carga no banco.
        synchronized (MAPA_TRAVAS.computeIfAbsent(chaveCache, chave -> new Object())) {
            try {
                cache = MAPA_CACHE.get(chaveCache);
                if (cache != null && System.currentTimeMillis() - (Long) cache[0] < MAPA_CACHE_MS) {
                    return Response.ok(cache[1]).build();
                }
                Response resposta = mapaInterno(inicio, fim, unidade, origem, "", true);
                if (resposta.getStatus() == 200) {
                    if (MAPA_CACHE.size() > 500) MAPA_CACHE.clear();
                    MAPA_CACHE.put(chaveCache, new Object[] { System.currentTimeMillis(), resposta.getEntity() });
                }
                return resposta;
            } finally {
                MAPA_TRAVAS.remove(chaveCache);
            }
        }
    }

    /** Recorte de uma doenca a partir do mapa completo. O seletor continua listando todas as doencas. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> filtrarPorDoenca(Map<String, Object> completo, String nome) {
        Map<String, Object> resposta = new LinkedHashMap<>(completo);
        List<Map<String, Object>> pontos = ((List<Map<String, Object>>) completo.getOrDefault("pontos", List.of())).stream()
                .filter(ponto -> nome.equals(ponto.get("doenca"))).toList();
        List<Map<String, Object>> pequenas = ((List<Map<String, Object>>) completo.getOrDefault("regioesAbaixoMinimo", List.of())).stream()
                .filter(item -> nome.equals(item.get("nome"))).toList();
        long casos = ((List<Map<String, Object>>) completo.getOrDefault("doencas", List.of())).stream()
                .filter(item -> nome.equals(item.get("nome")))
                .mapToLong(item -> ((Number) item.get("casos")).longValue()).findFirst().orElse(0);
        resposta.put("pontos", pontos);
        resposta.put("regioesAbaixoMinimo", pequenas);
        resposta.put("totalCasos", casos);
        resposta.put("casosNoMapa", pontos.stream().mapToLong(ponto -> ((Number) ponto.get("peso")).longValue()).sum());
        resposta.put("casosRegioesPequenas", pequenas.stream().mapToLong(item -> ((Number) item.get("casos")).longValue()).sum());
        return resposta;
    }

    private static final Map<String, Object> MAPA_TRAVAS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Uma regiao de uma doenca, montada a partir da varredura unica de mapaTodasDoencas. */
    private static final class RegiaoDoenca {
        final double latitude;
        final double longitude;
        final java.util.Set<String> atuais = new java.util.HashSet<>();
        final java.util.Set<String> anteriores = new java.util.HashSet<>();
        final java.util.Set<String> aproximados = new java.util.HashSet<>();
        final Map<String, Integer> nomes = new java.util.HashMap<>();
        final java.util.Set<String> confirmados = new java.util.HashSet<>();
        final java.util.Set<String> provaveis = new java.util.HashSet<>();

        RegiaoDoenca(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * Mapa sem filtro de doenca: uma so consulta classifica cada atendimento em todas as regras e os
     * pontos sao montados aqui. Antes era uma consulta geral e mais uma de 5 janelas por doenca da legenda,
     * o que passava do tempo de transacao. Mantem os mesmos limites e campos de mapaInterno.
     */
    private Response mapaTodasDoencas(LocalDate dataInicio, LocalDate dataFim, String unidade, String origem, List<Map<String, Object>> regras) {
        long diasPeriodo = java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        List<Object[]> rows = varrer(regras, dataInicio.minusDays(diasPeriodo * 4), dataFim, unidade, origem);
        // A decisao da vigilancia vale aqui tambem: descartado sai do mapa, dos totais e da media anterior.
        List<Map<String, String>> classificacoes = classificacoesPorIndice(regras);
        ClassificacaoCasoEpidemiologico.aplicar(rows, classificacoes);
        GeocodificacaoPacienteService.PadraoUnidade padraoUnidade = GeocodificacaoPacienteService.padraoDaUnidade(unidade);
        List<Map<String, RegiaoDoenca>> regioesPorRegra = new ArrayList<>();
        regras.forEach(regra -> regioesPorRegra.add(new LinkedHashMap<>()));
        java.util.Set<String> pacientesAtuais = new java.util.HashSet<>();
        java.util.Set<String> pacientesSemLocal = new java.util.HashSet<>();
        for (Object[] row : rows) {
            String paciente = texto(row[1]);
            boolean atual = !LocalDate.parse(texto(row[4]).substring(0, 10)).isBefore(dataInicio);
            if (atual && !texto(row[13]).isBlank()) pacientesAtuais.add(paciente);
            if (texto(row[13]).isBlank()) continue;
            double[] celula = celulaDoCaso((Number) row[5], (Number) row[6], texto(row[7]), texto(row[8]), texto(row[9]), padraoUnidade);
            if (celula == null) {
                if (atual) pacientesSemLocal.add(paciente);
                continue;
            }
            boolean aproximado = !temCoordenadaPropria((Number) row[5], (Number) row[6]);
            for (String indice : texto(row[13]).split(",")) {
                if (indice.isBlank()) continue;
                RegiaoDoenca regiao = regioesPorRegra.get(Integer.parseInt(indice))
                        .computeIfAbsent(celula[0] + ":" + celula[1], chave -> new RegiaoDoenca(celula[0], celula[1]));
                if (!atual) {
                    regiao.anteriores.add(paciente);
                    continue;
                }
                regiao.atuais.add(paciente);
                if (aproximado) regiao.aproximados.add(paciente);
                regiao.nomes.merge(texto(row[7]) + "|" + texto(row[8]), 1, Integer::sum);
                // O id do G-PEC pode coincidir com um BAM, e a classificacao so existe para BAM.
                String classificacao = "GHBAM".equals(row[0]) ? classificacoes.get(Integer.parseInt(indice)).get(texto(row[2])) : null;
                if (ClassificacaoCasoEpidemiologico.CONFIRMADO.equals(classificacao)) regiao.confirmados.add(paciente);
                if (ClassificacaoCasoEpidemiologico.PROVAVEL.equals(classificacao)) regiao.provaveis.add(paciente);
            }
        }
        long[][] contagemGhbam = contarDasLinhas(rows, regras.size(), dataInicio, dataFim, "GHBAM");
        long[][] contagemGpec = contarDasLinhas(rows, regras.size(), dataInicio, dataFim, "GPEC");
        List<Map<String, Object>> doencas = new ArrayList<>();
        List<Map<String, Object>> mencoesNegadas = new ArrayList<>();
        List<Map<String, Object>> semCriterio = new ArrayList<>();
        List<Map<String, Object>> regioesAbaixoMinimo = new ArrayList<>();
        List<Map<String, Object>> pontos = new ArrayList<>();
        long casosRegioesPequenasTotal = 0;
        for (int i = 0; i < regras.size(); i++) {
            Map<String, Object> regra = regras.get(i);
            String nome = texto(regra.get("nome"));
            LimitesRegra limites = limitesDe(regra);
            long casosGhbam = contagemGhbam[i][0];
            long casosGpec = contagemGpec[i][0];
            long casos = "GHBAM".equalsIgnoreCase(texto(origem)) ? casosGhbam
                    : "GPEC".equalsIgnoreCase(texto(origem)) ? casosGpec : casosGhbam + casosGpec;
            long negadas = contagemGhbam[i][1] + contagemGpec[i][1];
            long fora = contagemGhbam[i][2] + contagemGpec[i][2];
            // Caso confirmado sempre aparece, mesmo abaixo do minimo da doenca ou da regiao.
            boolean naLegenda = casos >= limites.minimo() || regioesPorRegra.get(i).values().stream().anyMatch(regiao -> !regiao.confirmados.isEmpty());
            if (negadas > 0 && (naLegenda || limites.cadastrada())) {
                Map<String, Object> mencao = new LinkedHashMap<>();
                mencao.put("nome", nome);
                mencao.put("casos", negadas);
                mencoesNegadas.add(mencao);
            }
            if (fora > 0 && limites.cadastrada()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("nome", nome);
                item.put("casos", fora);
                semCriterio.add(item);
            }
            if (!naLegenda) continue;
            // Ocorrencia aberta = doenca em curso na unidade: a regiao com casos nao volta a "dentro do esperado" no pico.
            boolean emCurso = limites.cadastrada() && ocorrenciaSistemaService.automaticaAberta(
                    ocorrenciaEpidemiologica(unidade, regra, new LinkedHashMap<>(), 0, 0)).isPresent();
            long pequenasDaDoenca = 0;
            boolean temPonto = false;
            for (RegiaoDoenca regiao : regioesPorRegra.get(i).values()) {
                long peso = regiao.atuais.size();
                if (peso <= 0) continue;
                if (peso < limites.minimoRegiao() && regiao.confirmados.isEmpty()) {
                    pequenasDaDoenca += peso;
                    continue;
                }
                temPonto = true;
                String[] nomes = regiao.nomes.entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(entrada -> entrada.getKey().split("\\|", -1)).orElse(new String[] { "", "" });
                double media = regiao.anteriores.size() / 4d;
                boolean alerta = peso >= limites.minimo() && (peso >= media * limites.fator() || emCurso);
                regiao.provaveis.removeAll(regiao.confirmados);
                Map<String, Object> ponto = new LinkedHashMap<>();
                ponto.put("emCurso", emCurso);
                ponto.put("casosConfirmados", (long) regiao.confirmados.size());
                ponto.put("casosProvaveis", (long) regiao.provaveis.size());
                ponto.put("latitude", regiao.latitude);
                ponto.put("longitude", regiao.longitude);
                ponto.put("peso", peso);
                ponto.put("bairro", nomes[0]);
                ponto.put("municipio", nomes.length > 1 ? nomes[1] : "");
                ponto.put("casosAproximados", (long) regiao.aproximados.size());
                ponto.put("origem", texto(origem).isBlank() ? "TODOS" : texto(origem));
                ponto.put("mediaAnterior", Math.round(media * 10) / 10d);
                ponto.put("sinalParaInvestigacao", alerta);
                ponto.put("nivel", alerta ? (peso >= limites.pesoAlto() || (media > 0 && peso >= media * 3) ? "ALTO" : "ATENCAO") : "NORMAL");
                ponto.put("doenca", nome);
                pontos.add(ponto);
            }
            adicionarRegioesAbaixoMinimo(regioesAbaixoMinimo, nome, pequenasDaDoenca);
            casosRegioesPequenasTotal += pequenasDaDoenca;
            // A legenda so lista doenca com regiao desenhavel: escolher outra deixaria o mapa vazio.
            if (temPonto) {
                Map<String, Object> opcao = new LinkedHashMap<>();
                opcao.put("id", regra.get("id"));
                opcao.put("nome", nome);
                opcao.put("termos", regra.get("termos"));
                opcao.put("casos", casos);
                opcao.put("casosGhbam", casosGhbam);
                opcao.put("casosGpec", casosGpec);
                opcao.put("casosMencaoNegada", negadas);
                doencas.add(opcao);
            }
        }
        pontos.sort((a, b) -> Long.compare(((Number) b.get("peso")).longValue(), ((Number) a.get("peso")).longValue()));
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("totalCasos", (long) pacientesAtuais.size());
        resposta.put("pontos", pontos);
        resposta.put("casosNoMapa", pontos.stream().mapToLong(p -> ((Number) p.get("peso")).longValue()).sum());
        resposta.put("casosSemLocalizacao", (long) pacientesSemLocal.size());
        resposta.put("casosRegioesPequenas", casosRegioesPequenasTotal);
        resposta.put("origem", texto(origem).isBlank() ? "GHBAM" : texto(origem));
        resposta.put("mencoesNegadas", mencoesNegadas);
        resposta.put("semCriterio", semCriterio);
        resposta.put("regioesAbaixoMinimo", regioesAbaixoMinimo);
        resposta.put("doencas", doencas);
        resposta.put("inicio", dataInicio.toString());
        resposta.put("fim", dataFim.toString());
        return Response.ok(resposta).build();
    }

    // Abaixo disso a doenca nem entra na legenda e nao gera sinal (definido pelo Marcelo).
    private static final int MINIMO_INVESTIGACAO = 20;
    private static final long MAPA_CACHE_MS = 5 * 60 * 1000L;
    private static final Map<String, Object[]> MAPA_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Texto normalizado do BAM: os mesmos campos no mapa, na legenda, na analise e na lista de casos. */
    static final String TEXTO_BAM_SQL = """
            ' ' || REGEXP_REPLACE(TRANSLATE(LOWER(
              COALESCE(r.queixaprincipal,'') || ' ' || COALESCE(r.obsentrada,'') || ' ' ||
              COALESCE(a.anamnese,'') || ' ' || COALESCE(a.comentario,'') || ' ' ||
              COALESCE(a.risco,'') || ' ' ||
              COALESCE(t.diagnostico_provisorio,'') || ' ' || COALESCE(t.evolucao_prescricao,'') || ' ' ||
              COALESCE(t.obs_enfermagem,'') || ' ' || COALESCE(ai.frente,'') || ' ' || COALESCE(ai.verso,'') || ' ' ||
              COALESCE((SELECT string_agg(e1.relato, ' ') FROM atendimento e1 WHERE e1.bam=b.idbam),'') || ' ' ||
              COALESCE((SELECT string_agg(e2.relato, ' ') FROM evolucaopaciente e2 WHERE e2.nbam=b.idbam AND e2.inativo IS NOT TRUE),'') || ' ' ||
              COALESCE((SELECT string_agg(e3.relato, ' ') FROM evolucaopacienteenf e3 WHERE e3.nbam=b.idbam),'') || ' ' ||
              COALESCE((SELECT string_agg(e4.diagnostico, ' ') FROM novointernado e4 WHERE e4.nbam=b.idbam),'')
            ), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' '
            """;

    /**
     * O BAM cumpre a definicao de caso depois de retirar as mencoes negadas. OFFSET 0 impede o
     * planner de repetir o regex a cada LIKE; o regex so roda nos textos que citam algum termo.
     */
    private static String filtroBamSql(String predicado) {
        return """
            SELECT 1 FROM registro r
            LEFT JOIN avaliacao a ON a.idavaliacao=b.avaliacao_idavaliacao
            LEFT JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
            LEFT JOIN anamneseinternacao ai ON ai.nbam=b.idbam
            CROSS JOIN LATERAL (SELECT """ + TEXTO_BAM_SQL + """
             AS txt OFFSET 0) x
            CROSS JOIN LATERAL (SELECT CASE WHEN x.txt ILIKE ANY(CAST(string_to_array(:padroesDoenca, '|') AS text[]))
                THEN REGEXP_REPLACE(x.txt, :negacao, ' ', 'g') ELSE '' END AS limpo OFFSET 0) y
            WHERE r.idregistro=b.registro_idregistro
              AND """ + predicado + "\n";
    }

    /** Criterio de uma ou mais regras: condicao SQL sobre y.limpo, termos citados, regex de negacao e padroes do G-PEC. */
    record CriterioCaso(String predicado, String padroesCitados, String negacao, String padroesGpec) {}

    CriterioCaso criterioCaso(List<Map<String, Object>> regras) {
        List<String> predicados = new ArrayList<>();
        java.util.Set<String> citados = new java.util.LinkedHashSet<>();
        java.util.Set<String> estruturados = new java.util.LinkedHashSet<>();
        for (Map<String, Object> regra : regras) {
            String nome = texto(regra.get("nome"));
            String termos = texto(regra.get("termos"));
            predicados.add(DefinicaoCasoEpidemiologico.predicadoSql("y.limpo", nome, termos));
            citados.addAll(DefinicaoCasoEpidemiologico.termosCitados(nome, termos));
            estruturados.addAll(DefinicaoCasoEpidemiologico.termosEstruturados(nome, termos));
        }
        return new CriterioCaso(predicados.isEmpty() ? "FALSE" : "(" + String.join(" OR ", predicados) + ")",
                padroesDe(citados), regexMencaoNegada(String.join(",", citados)), padroesDe(estruturados));
    }

    private static String padroesDe(java.util.Collection<String> termos) {
        return termos.stream().map(termo -> "% " + termo + " %").collect(java.util.stream.Collectors.joining("|"));
    }

    /** Regras ativas da unidade (sem filtro) ou a regra dos termos filtrados; termo avulso vira regra sem nome. */
    private List<Map<String, Object>> regrasDaConsulta(String unidade, String doenca) {
        List<Map<String, Object>> ativas = regrasMapa(unidade).stream()
                .filter(regra -> Boolean.TRUE.equals(regra.get("ativo")) && !texto(regra.get("termos")).isBlank())
                .toList();
        if (doenca.isBlank()) return ativas;
        List<Map<String, Object>> escolhidas = ativas.stream().filter(regra -> texto(regra.get("termos")).equals(doenca)).toList();
        return escolhidas.isEmpty() ? List.of(Map.<String, Object>of("nome", "", "termos", doenca)) : escolhidas;
    }

    private static void adicionarRegioesAbaixoMinimo(List<Map<String, Object>> lista, String nome, Object casos) {
        if (!(casos instanceof Number) || ((Number) casos).longValue() <= 0) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nome", nome);
        item.put("casos", ((Number) casos).longValue());
        lista.add(item);
    }

    private static void limparCaches() {
        MAPA_CACHE.clear();
        ALERTA_CACHE.clear();
        ANALISE_CACHE.clear();
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
            List<Map<String, Object>> regrasConsulta = regrasDaConsulta(unidade, texto(doenca));
            CriterioCaso criterio = criterioCaso(regrasConsulta);
            String termosDoenca = regrasConsulta.stream().map(regra -> texto(regra.get("termos"))).collect(java.util.stream.Collectors.joining(","));
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
            if (completo && texto(doenca).isBlank()) {
                return mapaTodasDoencas(dataInicio, dataFim, unidade, origem, regrasConsulta);
            }

            String sql = """
                    WITH fontes AS (
                        SELECT CAST(p.latitude AS double precision) AS latitude,
                               CAST(p.longitude AS double precision) AS longitude,
                               CAST(b.paciente_idpaciente AS varchar) AS origem_id,
                               NULLIF(UPPER(TRIM(p.bairro)), '') AS bairro,
                               NULLIF(UPPER(TRIM(p.municipio)), '') AS municipio,
                               NULLIF(UPPER(TRIM(p.estado)), '') AS estado,
                               CAST(b.data AS date) >= :inicio AS atual
                        FROM bam b
                        JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
                        WHERE CAST(b.data AS date) BETWEEN :inicioBase AND :fim
                          AND (:unidade = '' OR b.unidade = :unidade)
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GHBAM')
                          AND (:doenca = '' OR EXISTS (
                    """ + filtroBamSql(criterio.predicado()) + """
                          ))
                        UNION ALL
                        SELECT d.latitude, d.longitude,
                               CAST(CONCAT('GPEC-', a.id_cidadao) AS varchar) AS origem_id,
                               NULLIF(UPPER(TRIM(d.bairro)), '') AS bairro,
                               CAST(NULL AS varchar) AS municipio,
                               CAST(NULL AS varchar) AS estado,
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
                                    ILIKE ANY(CAST(string_to_array(:padroesGpec, '|') AS text[]))
                          ))
                    ), marcadas AS (
                        -- Sem coordenada, fora do Brasil ou no ponto padrao antigo de Itaguai
                        -- = sem localizacao propria; esses casos sao posicionados pelo bairro.
                        SELECT f.*, (latitude IS NOT NULL AND longitude IS NOT NULL
                                     AND latitude BETWEEN -34 AND 6 AND longitude BETWEEN -74 AND -33
                                     AND NOT (ABS(latitude - :latPadrao) < 0.000001
                                              AND ABS(longitude - :lonPadrao) < 0.000001)) AS localizado
                        FROM fontes f
                    ), concentracoes AS (
                        -- atual = periodo escolhido; o restante sao as 4 janelas anteriores
                        -- de mesmo tamanho, base historica da mesma regiao.
                        -- O ponto e o centro da celula (~1 km), nunca o endereco do paciente.
                        SELECT ROUND(CAST(latitude AS numeric), 2) AS latitude,
                               ROUND(CAST(longitude AS numeric), 2) AS longitude,
                               COUNT(DISTINCT origem_id) FILTER (WHERE atual) AS peso,
                               MODE() WITHIN GROUP (ORDER BY bairro) FILTER (WHERE atual) AS bairro,
                               MODE() WITHIN GROUP (ORDER BY municipio) FILTER (WHERE atual) AS municipio,
                               COUNT(DISTINCT origem_id) FILTER (WHERE NOT atual) AS base,
                               CAST(NULL AS varchar) AS estado
                        FROM marcadas
                        WHERE localizado
                        GROUP BY 1, 2
                        UNION ALL
                        SELECT NULL, NULL,
                               COUNT(DISTINCT origem_id) FILTER (WHERE atual),
                               bairro, municipio,
                               COUNT(DISTINCT origem_id) FILTER (WHERE NOT atual),
                               estado
                        FROM marcadas
                        WHERE NOT localizado
                        GROUP BY bairro, municipio, estado
                    )
                    SELECT latitude, longitude, peso, bairro, municipio, base, estado
                    FROM concentracoes
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
                    .setParameter("padroesDoenca", criterio.padroesCitados())
                    .setParameter("padroesGpec", criterio.padroesGpec())
                    .setParameter("negacao", criterio.negacao())
                    .setParameter("acentos", ACENTOS)
                    .setParameter("semAcentos", SEM_ACENTOS)
                    .setParameter("latPadrao", GeocodificacaoPacienteService.LATITUDE_PADRAO)
                    .setParameter("lonPadrao", GeocodificacaoPacienteService.LONGITUDE_PADRAO)
                    .getResultList();

            // Casos sem localizacao propria vao para o centro do bairro/municipio (ou o
            // ponto da unidade) e somam na celula de ~1 km correspondente. Celula:
            // [latitude, longitude, peso, base, aproximados, maior peso que deu o nome].
            Map<String, double[]> celulas = new LinkedHashMap<>();
            Map<String, String[]> nomesCelula = new LinkedHashMap<>();
            GeocodificacaoPacienteService.PadraoUnidade padraoUnidade = null;
            long casosSemLocalizacao = 0;
            for (Object[] row : rows) {
                long pesoLinha = ((Number) row[2]).longValue();
                boolean aproximado = row[0] == null || row[1] == null;
                double[] coordenada;
                if (aproximado) {
                    if (padraoUnidade == null) padraoUnidade = GeocodificacaoPacienteService.padraoDaUnidade(unidade);
                    coordenada = GeocodificacaoPacienteService.coordenadaAproximada(texto(row[3]), texto(row[4]), texto(row[6]), padraoUnidade);
                    if (coordenada == null) {
                        casosSemLocalizacao += pesoLinha;
                        continue;
                    }
                } else {
                    coordenada = new double[] { ((Number) row[0]).doubleValue(), ((Number) row[1]).doubleValue() };
                }
                double latitude = Math.round(coordenada[0] * 100) / 100d;
                double longitude = Math.round(coordenada[1] * 100) / 100d;
                String chave = latitude + ":" + longitude;
                double[] celula = celulas.computeIfAbsent(chave, k -> new double[] { latitude, longitude, 0, 0, 0, -1 });
                celula[2] += pesoLinha;
                celula[3] += ((Number) row[5]).longValue();
                if (aproximado) celula[4] += pesoLinha;
                if (pesoLinha > celula[5]) {
                    celula[5] = pesoLinha;
                    nomesCelula.put(chave, new String[] { texto(row[3]), texto(row[4]) });
                }
            }

            String totalSql = """
                    SELECT COUNT(DISTINCT origem_id) FROM (
                        SELECT CAST(b.paciente_idpaciente AS varchar) AS origem_id
                        FROM bam b
                        WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim
                          AND (:unidade = '' OR b.unidade = :unidade)
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GHBAM')
                          AND (:doenca = '' OR EXISTS (
                    """ + filtroBamSql(criterio.predicado()) + """
                          ))
                        UNION ALL
                        SELECT CAST(CONCAT('GPEC-', a.id_cidadao) AS varchar) AS origem_id
                        FROM aps_atendimento a
                        WHERE a.data_referencia BETWEEN :inicio AND :fim
                          AND a.status <> 'CANCELADO'
                          AND (:unidade = '' OR UPPER(a.unidade) = UPPER(:unidade))
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GPEC')
                          AND (:doenca = '' OR EXISTS (
                              SELECT 1 FROM aps_problema pr
                              WHERE pr.id_atendimento = a.id AND pr.tipo = 'PROBLEMA'
                                AND (' ' || REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(pr.descricao,'') || ' ' || COALESCE(pr.cid10,'') || ' ' || COALESCE(pr.ciap,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g') || ' ')
                                    ILIKE ANY(CAST(string_to_array(:padroesGpec, '|') AS text[]))
                          ))
                    ) fontes
                    """;
            Number total = !completo ? Integer.valueOf(0) : (Number) em.createNativeQuery(totalSql)
                    .setParameter("inicio", dataInicio)
                    .setParameter("fim", dataFim)
                    .setParameter("unidade", texto(unidade))
                    .setParameter("origem", texto(origem))
                    .setParameter("doenca", termosDoenca)
                    .setParameter("padroesDoenca", criterio.padroesCitados())
                    .setParameter("padroesGpec", criterio.padroesGpec())
                    .setParameter("negacao", criterio.negacao())
                    .setParameter("acentos", ACENTOS)
                    .setParameter("semAcentos", SEM_ACENTOS)
                    .getSingleResult();

            // Com doenca filtrada valem o minimo e o fator da regra; o catalogo padrao segue o minimo geral.
            LimitesRegra limites = limitesDaRegra(unidade, texto(doenca));
            List<Map<String, Object>> pontos = new ArrayList<>();
            long casosRegioesPequenas = 0;
            for (Map.Entry<String, double[]> entrada : celulas.entrySet()) {
                double[] celula = entrada.getValue();
                long peso = (long) celula[2];
                if (peso <= 0) continue;
                if (peso < limites.minimoRegiao()) {
                    casosRegioesPequenas += peso;
                    continue;
                }
                String[] nomes = nomesCelula.getOrDefault(entrada.getKey(), new String[] { "", "" });
                Map<String, Object> ponto = new LinkedHashMap<>();
                ponto.put("latitude", celula[0]);
                ponto.put("longitude", celula[1]);
                ponto.put("peso", peso);
                // Bairro e município predominantes da região (agregado, nunca do paciente).
                ponto.put("bairro", nomes[0]);
                ponto.put("municipio", nomes[1]);
                // Casos posicionados pelo bairro/município/unidade, sem endereço localizado.
                ponto.put("casosAproximados", (long) celula[4]);
                ponto.put("origem", texto(origem).isBlank() ? "TODOS" : texto(origem));
                // Mesma regra de sinal do analisar(): minimo e fator sobre a media das
                // janelas anteriores de mesmo tamanho.
                double media = celula[3] / 4d;
                boolean alerta = peso >= limites.minimo() && peso >= media * limites.fator();
                ponto.put("mediaAnterior", Math.round(media * 10) / 10d);
                ponto.put("sinalParaInvestigacao", alerta);
                ponto.put("nivel", alerta ? (peso >= limites.pesoAlto() || (media > 0 && peso >= media * 3) ? "ALTO" : "ATENCAO") : "NORMAL");
                pontos.add(ponto);
            }
            pontos.sort((a, b) -> Long.compare(((Number) b.get("peso")).longValue(), ((Number) a.get("peso")).longValue()));

            long casosNoMapa = pontos.stream()
                    .mapToLong(p -> ((Number) p.get("peso")).longValue())
                    .sum();
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("totalCasos", total == null ? 0 : total.longValue());
            resposta.put("pontos", pontos);
            resposta.put("casosNoMapa", casosNoMapa);
            resposta.put("casosSemLocalizacao", casosSemLocalizacao);
            resposta.put("casosRegioesPequenas", casosRegioesPequenas);
            resposta.put("origem", texto(origem).isBlank() ? "GHBAM" : texto(origem));
            List<Map<String, Object>> doencas = new ArrayList<>();
            List<Map<String, Object>> regrasAtivas = new ArrayList<>();
            if (completo) {
                for (Map<String, Object> regra : regrasMapa(unidade)) {
                    if (Boolean.TRUE.equals(regra.get("ativo"))) regrasAtivas.add(regra);
                }
            }
            // Uma consulta por fonte conta todas as doencas (antes: 2 consultas por doenca).
            long[][] contagemGhbam = "GPEC".equalsIgnoreCase(texto(origem)) ? new long[regrasAtivas.size()][3]
                    : contarPorRegra(unidade, regrasAtivas, dataInicio, dataFim, false);
            long[][] contagemGpec = "GHBAM".equalsIgnoreCase(texto(origem)) ? new long[regrasAtivas.size()][3]
                    : contarPorRegra(unidade, regrasAtivas, dataInicio, dataFim, true);
            // Atendimentos que so citam o termo negando: fora da contagem, mas visiveis para conferencia.
            List<Map<String, Object>> mencoesNegadas = new ArrayList<>();
            List<Map<String, Object>> semCriterio = new ArrayList<>();
            for (int indiceRegra = 0; indiceRegra < regrasAtivas.size(); indiceRegra++) {
                Map<String, Object> regra = regrasAtivas.get(indiceRegra);
                long casosGhbam = contagemGhbam[indiceRegra][0];
                long casosGpec = contagemGpec[indiceRegra][0];
                long negadas = contagemGhbam[indiceRegra][1] + contagemGpec[indiceRegra][1];
                long casos = "GHBAM".equalsIgnoreCase(texto(origem)) ? casosGhbam
                        : "GPEC".equalsIgnoreCase(texto(origem)) ? casosGpec : casosGhbam + casosGpec;
                LimitesRegra limitesLegenda = limitesDe(regra);
                boolean naLegenda = casos >= limitesLegenda.minimo();
                if (naLegenda) {
                    Map<String, Object> opcao = new LinkedHashMap<>();
                    opcao.put("id", regra.get("id"));
                    opcao.put("nome", regra.get("nome"));
                    opcao.put("termos", regra.get("termos"));
                    opcao.put("casos", casos);
                    opcao.put("casosGhbam", casosGhbam);
                    opcao.put("casosGpec", casosGpec);
                    opcao.put("casosMencaoNegada", negadas);
                    doencas.add(opcao);
                }
                if (negadas > 0 && (naLegenda || limitesLegenda.cadastrada())) {
                    Map<String, Object> mencao = new LinkedHashMap<>();
                    mencao.put("nome", regra.get("nome"));
                    mencao.put("casos", negadas);
                    mencoesNegadas.add(mencao);
                }
                long foraDoCriterio = contagemGhbam[indiceRegra][2] + contagemGpec[indiceRegra][2];
                if (foraDoCriterio > 0 && limitesLegenda.cadastrada()) {
                    Map<String, Object> fora = new LinkedHashMap<>();
                    fora.put("nome", regra.get("nome"));
                    fora.put("casos", foraDoCriterio);
                    semCriterio.add(fora);
                }
            }
            resposta.put("mencoesNegadas", mencoesNegadas);
            resposta.put("semCriterio", semCriterio);
            List<Map<String, Object>> regioesAbaixoMinimo = new ArrayList<>();
            resposta.put("regioesAbaixoMinimo", regioesAbaixoMinimo);
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
                    adicionarRegioesAbaixoMinimo(regioesAbaixoMinimo, texto(doencaMapa.get("nome")),
                            ((Map<?, ?>) respostaDoenca.getEntity()).get("casosRegioesPequenas"));
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
                adicionarRegioesAbaixoMinimo(regioesAbaixoMinimo, nomeDoenca, casosRegioesPequenas);
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
                               CAST(b.paciente_idpaciente AS varchar) AS origem_id
                        FROM bam b
                        JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                        WHERE CAST(b.data AS date) BETWEEN :inicioBase AND :fim
                          AND b.unidade = :unidade
                          AND COALESCE(TRIM(t.diagnostico_provisorio), '') <> ''
                          AND NOT (b.idbam = ANY(CAST(:descartados AS int[])))
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GHBAM')
                        UNION ALL
                        SELECT 'GPEC',
                               UPPER(COALESCE(NULLIF(TRIM(pr.cid10), ''), NULLIF(TRIM(pr.ciap), ''),
                                     LEFT(TRIM(TRANSLATE(LOWER(pr.descricao), :acentos, :semAcentos)), 80))),
                               pr.descricao,
                               a.data_referencia >= :inicio,
                               CAST(CONCAT('GPEC-', a.id_cidadao) AS varchar)
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
                    .setParameter("descartados", bamsDescartadosDosAcumulos(unidade, dataInicio.minusDays(dias * 4), dataFim))
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

    /**
     * Pacientes do periodo, um por paciente, com o atendimento mais recente e as doencas em que
     * se enquadram. Doenca e regiao sao filtros opcionais. Fora de /mapa de proposito: o RBAC so
     * libera a vigilancia, a CCIH e a administracao; medico e enfermagem seguem nos agregados.
     */
    @GET @Path("regiao/casos")
    @Transactional
    public Response casosDaRegiao(@QueryParam("inicio") String inicio,
                                  @QueryParam("fim") String fim,
                                  @QueryParam("unidade") String unidadeInformada,
                                  @QueryParam("origem") String origem,
                                  @QueryParam("doenca") String doenca,
                                  @QueryParam("latitude") Double latitude,
                                  @QueryParam("longitude") Double longitude) {
        try {
            LocalDate dataInicio = LocalDate.parse(texto(inicio));
            LocalDate dataFim = LocalDate.parse(texto(fim));
            String unidade = unidadeEfetiva(unidadeInformada);
            if (dataFim.isBefore(dataInicio) || dataInicio.plusDays(92).isBefore(dataFim)
                    || unidade.isBlank() || (latitude == null) != (longitude == null)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "Informe período de até 93 dias, unidade e região completa.")).build();
            }
            garantirEstrutura();
            List<Map<String, Object>> regrasConsulta = regrasDaConsulta(unidade, texto(doenca));
            if (regrasConsulta.isEmpty()) return Response.ok(Map.of("casos", List.of())).build();
            List<Object[]> rows = varrer(regrasConsulta, dataInicio, dataFim, unidade, origem);
            List<Map<String, String>> classificacoes = classificacoesPorIndice(regrasConsulta);
            ClassificacaoCasoEpidemiologico.aplicar(rows, classificacoes);
            GeocodificacaoPacienteService.PadraoUnidade padraoUnidade = GeocodificacaoPacienteService.padraoDaUnidade(unidade);
            Map<String, Map<String, Object>> porPaciente = new LinkedHashMap<>();
            for (Object[] row : rows) {
                // Quem so cita termos, sem cumprir a definicao, nao e caso.
                if (texto(row[13]).isBlank()) continue;
                double[] celula = celulaDoCaso((Number) row[5], (Number) row[6], texto(row[7]), texto(row[8]), texto(row[9]), padraoUnidade);
                if (latitude != null && (celula == null
                        || Math.abs(celula[0] - Math.round(latitude * 100) / 100d) > 0.000001
                        || Math.abs(celula[1] - Math.round(longitude * 100) / 100d) > 0.000001)) continue;
                // A consulta vem do mais recente para o mais antigo: a primeira linha do paciente e a que aparece.
                Map<String, Object> caso = porPaciente.get(texto(row[1]));
                if (caso == null) {
                    caso = new LinkedHashMap<>();
                    caso.put("fonte", texto(row[0]));
                    caso.put("atendimento", texto(row[2]));
                    caso.put("paciente", texto(row[3]));
                    caso.put("data", texto(row[4]));
                    caso.put("bairro", texto(row[7]));
                    caso.put("latitude", celula == null ? null : celula[0]);
                    caso.put("longitude", celula == null ? null : celula[1]);
                    caso.put("setor", texto(row[10]));
                    caso.put("queixa", texto(row[11]));
                    caso.put("diagnostico", texto(row[12]));
                    caso.put("atendimentos", new java.util.LinkedHashSet<String>());
                    caso.put("doencas", new java.util.LinkedHashSet<String>());
                    porPaciente.put(texto(row[1]), caso);
                }
                @SuppressWarnings("unchecked") java.util.Set<String> atendimentos = (java.util.Set<String>) caso.get("atendimentos");
                atendimentos.add(texto(row[2]));
                @SuppressWarnings("unchecked") java.util.Set<String> doencas = (java.util.Set<String>) caso.get("doencas");
                for (String indice : texto(row[13]).split(",")) {
                    if (indice.isBlank()) continue;
                    doencas.add(texto(regrasConsulta.get(Integer.parseInt(indice)).get("nome")));
                    if ("GHBAM".equals(row[0])) {
                        caso.put("classificacao", ClassificacaoCasoEpidemiologico.maisForte(texto(caso.get("classificacao")),
                                classificacoes.get(Integer.parseInt(indice)).get(texto(row[2]))));
                    }
                }
            }
            List<Map<String, Object>> casos = new ArrayList<>();
            for (Map<String, Object> caso : porPaciente.values()) {
                caso.put("atendimentos", ((java.util.Set<?>) caso.get("atendimentos")).size());
                caso.put("doencas", new ArrayList<>((java.util.Set<?>) caso.get("doencas")));
                casos.add(caso);
            }
            // Rastreabilidade do acesso a dado identificado; o log nao leva dado do paciente.
            var usuario = securityPermissionService.resolve(requestContext);
            LOG.infof("Casos do mapa consultados: unidade=%s, matricula=%s, pacientes=%d", unidade,
                    usuario == null ? "" : usuario.matricula(), casos.size());
            return Response.ok(Map.of("casos", casos)).build();
        } catch (java.time.format.DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "As datas devem estar no formato YYYY-MM-DD.")).build();
        } catch (Exception e) {
            LOG.error("Falha ao listar casos do mapa", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel listar os casos.")).build();
        }
    }

    /** Atendimentos que citam termos das regras, ja com o texto normalizado na ultima coluna. O regex fica no Java: no banco custava dezenas de segundos. */
    static String sqlVarredura() {
        return """
                    SELECT * FROM (
                        SELECT 'GHBAM' AS fonte, CAST(b.paciente_idpaciente AS varchar) AS paciente_id, CAST(b.idbam AS varchar) AS atendimento,
                               p.nome AS paciente, b.data AS data_caso,
                               CAST(p.latitude AS double precision) AS lat, CAST(p.longitude AS double precision) AS lon,
                               NULLIF(UPPER(TRIM(p.bairro)), '') AS bairro, NULLIF(UPPER(TRIM(p.municipio)), '') AS municipio, NULLIF(UPPER(TRIM(p.estado)), '') AS estado,
                               r.setor AS setor, r.queixaprincipal AS queixa, t.diagnostico_provisorio AS diagnostico, x.txt AS txt
                        FROM bam b
                        JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
                        JOIN registro r ON r.idregistro = b.registro_idregistro
                        LEFT JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao
                        LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                        LEFT JOIN anamneseinternacao ai ON ai.nbam = b.idbam
                        CROSS JOIN LATERAL (SELECT """ + TEXTO_BAM_SQL + """
                         AS txt OFFSET 0) x
                        WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim
                          AND (:unidade = '' OR b.unidade = :unidade)
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GHBAM')
                          AND x.txt LIKE ANY(CAST(string_to_array(:padroes, '|') AS text[]))
                        UNION ALL
                        SELECT 'GPEC', CONCAT('GPEC-', a.id_cidadao), CAST(a.id AS varchar),
                               COALESCE(NULLIF(TRIM(c.nome_social), ''), c.nome), a.data_referencia,
                               CAST(d.latitude AS double precision), CAST(d.longitude AS double precision),
                               NULLIF(UPPER(TRIM(d.bairro)), ''), CAST(NULL AS varchar), CAST(NULL AS varchar),
                               CAST(NULL AS varchar), CAST(NULL AS varchar), pt.descricao, pt.txt
                        FROM aps_atendimento a
                        JOIN aps_cidadao c ON c.id = a.id_cidadao
                        LEFT JOIN aps_familia f ON f.id = c.id_familia
                        LEFT JOIN aps_domicilio d ON d.id = f.id_domicilio
                        CROSS JOIN LATERAL (
                            SELECT MIN(pr.descricao) AS descricao,
                                   ' ' || string_agg(REGEXP_REPLACE(TRANSLATE(LOWER(COALESCE(pr.descricao,'') || ' ' || COALESCE(pr.cid10,'') || ' ' || COALESCE(pr.ciap,'')), :acentos, :semAcentos), '[^a-z0-9]+', ' ', 'g'), ' ') || ' ' AS txt
                            FROM aps_problema pr
                            WHERE pr.id_atendimento = a.id AND pr.tipo = 'PROBLEMA'
                        ) pt
                        WHERE a.data_referencia BETWEEN :inicio AND :fim
                          AND a.status <> 'CANCELADO'
                          AND (:unidade = '' OR UPPER(a.unidade) = UPPER(:unidade))
                          AND COALESCE(NULLIF(UPPER(:origem), ''), 'TODOS') IN ('TODOS', 'GPEC')
                          AND pt.txt IS NOT NULL
                    ) casos
                    ORDER BY data_caso DESC, atendimento DESC
                    """;
    }

    /** Colunas 0-12 como na consulta; 13 = regras em que e caso, 14 = cumpre antes de retirar a negacao, 15 = cita termos. */
    private List<Object[]> varrer(List<Map<String, Object>> regras, LocalDate inicio, LocalDate fim, String unidade, String origem) {
        CriterioCaso criterio = criterioCaso(regras);
        @SuppressWarnings("unchecked")
        List<Object[]> brutas = em.createNativeQuery(sqlVarredura())
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("unidade", texto(unidade))
                .setParameter("origem", texto(origem))
                .setParameter("padroes", criterio.padroesCitados())
                .setParameter("acentos", ACENTOS)
                .setParameter("semAcentos", SEM_ACENTOS)
                .getResultList();
        return classificar(brutas, regras, criterio);
    }

    /** Aplica a definicao de caso de cada regra. Sem acesso a banco, para poder ser medido e testado. */
    List<Object[]> classificar(List<Object[]> brutas, List<Map<String, Object>> regras, CriterioCaso criterio) {
        int total = regras.size();
        List<java.util.function.Predicate<String>> definicoes = new ArrayList<>();
        List<List<String>> citados = new ArrayList<>();
        List<List<String>> estruturados = new ArrayList<>();
        for (Map<String, Object> regra : regras) {
            String nome = texto(regra.get("nome"));
            String termos = texto(regra.get("termos"));
            definicoes.add(DefinicaoCasoEpidemiologico.compilar(nome, termos));
            citados.add(comEspacos(DefinicaoCasoEpidemiologico.termosCitados(nome, termos)));
            estruturados.add(comEspacos(DefinicaoCasoEpidemiologico.termosEstruturados(nome, termos)));
        }
        java.util.regex.Pattern negacao = java.util.regex.Pattern.compile(criterio.negacao());
        List<Object[]> linhas = new ArrayList<>();
        for (Object[] bruta : brutas) {
            String txt = texto(bruta[13]);
            if (!txt.startsWith(" ")) txt = " " + txt;
            if (!txt.endsWith(" ")) txt = txt + " ";
            boolean gpec = "GPEC".equals(bruta[0]);
            String limpo = gpec ? txt : negacao.matcher(txt).replaceAll(" ");
            List<String> casos = new ArrayList<>();
            List<String> cumprem = new ArrayList<>();
            List<String> citam = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                String indice = String.valueOf(i);
                if (gpec) {
                    if (contemAlgum(txt, estruturados.get(i))) {
                        casos.add(indice);
                        cumprem.add(indice);
                        citam.add(indice);
                    }
                    continue;
                }
                if (definicoes.get(i).test(limpo)) casos.add(indice);
                if (definicoes.get(i).test(txt)) cumprem.add(indice);
                if (contemAlgum(txt, citados.get(i))) citam.add(indice);
            }
            if (citam.isEmpty()) continue;
            Object[] linha = java.util.Arrays.copyOf(bruta, 16);
            linha[13] = String.join(",", casos);
            linha[14] = String.join(",", cumprem);
            linha[15] = String.join(",", citam);
            linhas.add(linha);
        }
        return linhas;
    }

    private static List<String> comEspacos(List<String> termos) {
        return termos.stream().map(termo -> " " + termo + " ").toList();
    }

    private static boolean contemAlgum(String texto, List<String> termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) return true;
        }
        return false;
    }

    /** {casos, so com mencao negada, citam termos sem cumprir a definicao} por regra, contando cada paciente uma vez. */
    long[][] contarDasLinhas(List<Object[]> linhas, int totalRegras, LocalDate inicio, LocalDate fim, String fonte) {
        List<java.util.Set<String>> casos = new ArrayList<>();
        List<java.util.Set<String>> cumprem = new ArrayList<>();
        List<java.util.Set<String>> citam = new ArrayList<>();
        for (int i = 0; i < totalRegras; i++) {
            casos.add(new java.util.HashSet<>());
            cumprem.add(new java.util.HashSet<>());
            citam.add(new java.util.HashSet<>());
        }
        for (Object[] linha : linhas) {
            if (!fonte.equals(linha[0])) continue;
            LocalDate data = LocalDate.parse(texto(linha[4]).substring(0, 10));
            if (data.isBefore(inicio) || data.isAfter(fim)) continue;
            String paciente = texto(linha[1]);
            marcar(casos, texto(linha[13]), paciente);
            marcar(cumprem, texto(linha[14]), paciente);
            marcar(citam, texto(linha[15]), paciente);
        }
        long[][] contagem = new long[totalRegras][3];
        for (int i = 0; i < totalRegras; i++) {
            contagem[i] = new long[] { casos.get(i).size(), cumprem.get(i).size() - casos.get(i).size(), citam.get(i).size() - cumprem.get(i).size() };
        }
        return contagem;
    }

    private static void marcar(List<java.util.Set<String>> conjuntos, String indices, String paciente) {
        for (String indice : indices.split(",")) {
            if (!indice.isBlank()) conjuntos.get(Integer.parseInt(indice)).add(paciente);
        }
    }

    /** Coordenada no Brasil e fora do ponto padrao antigo de Itaguai. */
    private static boolean temCoordenadaPropria(Number lat, Number lon) {
        if (lat == null || lon == null) return false;
        double latitude = lat.doubleValue();
        double longitude = lon.doubleValue();
        boolean padrao = Math.abs(latitude - GeocodificacaoPacienteService.LATITUDE_PADRAO) < 0.000001
                && Math.abs(longitude - GeocodificacaoPacienteService.LONGITUDE_PADRAO) < 0.000001;
        return latitude >= -34 && latitude <= 6 && longitude >= -74 && longitude <= -33 && !padrao;
    }

    /** Mesma celula do mapa: coordenada propria valida ou posicao aproximada pelo bairro/municipio/unidade. */
    static double[] celulaDoCaso(Number lat, Number lon, String bairro, String municipio, String estado,
                                         GeocodificacaoPacienteService.PadraoUnidade padraoUnidade) {
        if (temCoordenadaPropria(lat, lon)) {
            // HALF_UP afasta do zero, como o ROUND(numeric, 2) da consulta do mapa.
            return new double[] {
                java.math.BigDecimal.valueOf(lat.doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue(),
                java.math.BigDecimal.valueOf(lon.doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue() };
        }
        double[] aproximada = GeocodificacaoPacienteService.coordenadaAproximada(bairro, municipio, estado, padraoUnidade);
        return aproximada == null ? null : new double[] { Math.round(aproximada[0] * 100) / 100d, Math.round(aproximada[1] * 100) / 100d };
    }

    private static final long ALERTA_CACHE_MS = 10 * 60 * 1000L;
    private static final Map<String, Object[]> ALERTA_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Object[]> ANALISE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** Unidades com calculo do alerta em andamento: evita que o cache vencido dispare N calculos pesados ao mesmo tempo. */
    private static final Map<String, Boolean> ALERTA_CALCULANDO = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Resumo para o alerta do cabecalho (medico/enfermagem): regioes e diagnosticos
     * acima do esperado nos ultimos 7 dias da unidade. Cache de 10 min por unidade,
     * pois todos os profissionais consultam periodicamente.
     */
    @GET @Path("alerta")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response alerta(@QueryParam("unidade") String unidade) {
        String chave = "";
        try {
            chave = unidadeEfetiva(unidade);
            if (chave.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "Unidade de trabalho nao identificada.")).build();
            }
            Object[] cache = ALERTA_CACHE.get(chave);
            if (cache != null && cache.length >= 2 && cache[0] instanceof Long geradoEm
                    && System.currentTimeMillis() - geradoEm < ALERTA_CACHE_MS) {
                return Response.ok(cache[1]).build();
            }
            if (ALERTA_CALCULANDO.putIfAbsent(chave, Boolean.TRUE) != null) {
                // Outro profissional ja esta calculando: devolve o ultimo resultado (mesmo vencido)
                // ou um resumo neutro, sem cachear, ate o calculo terminar.
                if (cache != null && cache.length >= 2) return Response.ok(cache[1]).build();
                Map<String, Object> calculando = new LinkedHashMap<>();
                calculando.put("regioes", 0); calculando.put("diagnosticos", 0); calculando.put("total", 0);
                calculando.put("nivel", "NORMAL"); calculando.put("calculando", true);
                return Response.ok(calculando).build();
            }
            try {
                return calcularAlerta(chave);
            } finally {
                ALERTA_CALCULANDO.remove(chave);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao calcular alerta epidemiologico. unidade=%s", chave);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel calcular o alerta epidemiologico.")).build();
        }
    }

    @SuppressWarnings("unchecked")
    private Response calcularAlerta(String chave) {
        try {
            garantirEstrutura();
            if (!alertaCabecalhoAtivo(chave)) {
                // Desligado pela unidade: nem calcula, e o cabecalho esconde o selo (total 0).
                Map<String, Object> desligado = new LinkedHashMap<>();
                desligado.put("regioes", 0); desligado.put("diagnosticos", 0); desligado.put("total", 0);
                desligado.put("nivel", "NORMAL"); desligado.put("desativado", true);
                ALERTA_CACHE.put(chave, new Object[] { System.currentTimeMillis(), desligado });
                return Response.ok(desligado).build();
            }
            String inicio = LocalDate.now().minusDays(6).toString();
            String fim = LocalDate.now().toString();
            Response mapa = mapa(inicio, fim, chave, "TODOS", "");
            Response emergentes = emergentes(inicio, fim, chave, "TODOS");
            if (mapa.getStatus() != 200 || emergentes.getStatus() != 200) {
                LOG.warnf("Calculo do alerta epidemiologico indisponivel. unidade=%s, mapa=%d, emergentes=%d",
                        chave, mapa.getStatus(), emergentes.getStatus());
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("erro", "Nao foi possivel calcular o alerta epidemiologico.")).build();
            }
            Object mapaEntity = mapa.getEntity();
            Object emergentesEntity = emergentes.getEntity();
            if (!(mapaEntity instanceof Map<?, ?> mapaDados) || !(emergentesEntity instanceof Map<?, ?> emergentesDados)
                    || !(mapaDados.get("pontos") instanceof List<?> pontos)
                    || !(emergentesDados.get("itens") instanceof List<?> itens)) {
                LOG.errorf("Resposta interna invalida ao calcular alerta epidemiologico. unidade=%s", chave);
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("erro", "Nao foi possivel calcular o alerta epidemiologico.")).build();
            }
            long regioes = pontos.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .filter(p -> Boolean.TRUE.equals(p.get("sinalParaInvestigacao")))
                    .map(p -> texto(p.get("latitude")) + ":" + texto(p.get("longitude")))
                    .distinct().count();
            boolean alto = itens.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .anyMatch(i -> "ALTO".equals(i.get("nivel")));
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("regioes", regioes);
            resposta.put("diagnosticos", itens.size());
            resposta.put("total", regioes + itens.size());
            resposta.put("nivel", alto ? "ALTO" : (regioes + itens.size() > 0 ? "ATENCAO" : "NORMAL"));
            resposta.put("inicio", inicio);
            resposta.put("fim", fim);
            ALERTA_CACHE.put(chave, new Object[] { System.currentTimeMillis(), resposta });
            return Response.ok(resposta).build();
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao calcular alerta epidemiologico. unidade=%s", chave);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel calcular o alerta epidemiologico.")).build();
        }
    }

    private static final Map<String, Object[]> SINDROMES_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Boolean> SINDROMES_CALCULANDO = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Vigilancia sindromica para o especialista: sindromes do periodo, doencas compativeis (diferencial),
     * grau de sustentacao dos casos e escala do evento (aglomerado, possivel surto, possivel epidemia).
     * So agregados, sem nome de paciente; nada aqui confirma doenca. Cache de 10 min por unidade e periodo.
     */
    @GET @Path("sindromes")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response sindromes(@QueryParam("inicio") String inicio, @QueryParam("fim") String fim, @QueryParam("unidade") String unidade) {
        String chave = "";
        try {
            LocalDate dataFim = texto(fim).isBlank() ? LocalDate.now() : LocalDate.parse(texto(fim));
            LocalDate dataInicio = texto(inicio).isBlank() ? dataFim.minusDays(6) : LocalDate.parse(texto(inicio));
            if (dataFim.isBefore(dataInicio) || dataInicio.plusDays(30).isBefore(dataFim)) {
                return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Informe um intervalo de até 31 dias.")).build();
            }
            String unidadeSessao = unidadeEfetiva(unidade);
            if (unidadeSessao.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("erro", "Unidade de trabalho nao identificada. Saia e entre novamente selecionando a unidade no login.")).build();
            }
            chave = unidadeSessao + "|" + dataInicio + "|" + dataFim;
            Object[] cache = SINDROMES_CACHE.get(chave);
            if (cache != null && cache[0] instanceof Long geradoEm && System.currentTimeMillis() - geradoEm < ALERTA_CACHE_MS) {
                return Response.ok(cache[1]).build();
            }
            if (SINDROMES_CALCULANDO.putIfAbsent(chave, Boolean.TRUE) != null) {
                if (cache != null) return Response.ok(cache[1]).build();
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("erro", "A análise por síndrome já está sendo calculada. Tente novamente em instantes.")).build();
            }
            try {
                long dias = java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
                LocalDate inicioBase = dataInicio.minusDays(dias * 4);
                List<Object[]> brutas = em.createNativeQuery(sqlVarredura())
                        .setParameter("inicio", inicioBase)
                        .setParameter("fim", dataFim)
                        .setParameter("unidade", unidadeSessao)
                        .setParameter("origem", "TODOS")
                        .setParameter("padroes", padroesDe(SindromeEpidemiologica.termosDeBusca()))
                        .setParameter("acentos", ACENTOS)
                        .setParameter("semAcentos", SEM_ACENTOS)
                        .getResultList();
                // Negacao ("nega febre", "dengue descartada") sai antes; o G-PEC e problema estruturado, sem negacao.
                java.util.regex.Pattern negacao = java.util.regex.Pattern.compile(
                        regexMencaoNegada(String.join(",", SindromeEpidemiologica.termosNegaveis())));
                List<SindromeEpidemiologica.Caso> casos = new ArrayList<>();
                for (Object[] row : brutas) {
                    String txt = " " + texto(row[13]).trim() + " ";
                    String limpo = "GPEC".equals(row[0]) ? txt : negacao.matcher(txt).replaceAll(" ");
                    casos.add(new SindromeEpidemiologica.Caso(texto(row[1]), LocalDate.parse(texto(row[4]).substring(0, 10)), texto(row[7]), limpo));
                }
                double atendimentosAtuais = atendimentos(unidadeSessao, dataInicio, dataFim);
                double atendimentosReferencia = atendimentos(unidadeSessao, inicioBase, dataInicio.minusDays(1)) / 4d;
                Map<String, Object> painel = SindromeEpidemiologica.painel(casos, dataInicio, dataFim,
                        AvaliacaoSinalEpidemiologico.ajustePorAtendimentos(atendimentosAtuais, atendimentosReferencia));
                painel.put("unidade", unidadeSessao);
                SINDROMES_CACHE.put(chave, new Object[] { System.currentTimeMillis(), painel });
                return Response.ok(painel).build();
            } finally {
                SINDROMES_CALCULANDO.remove(chave);
            }
        } catch (java.time.format.DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Data inválida. Use o formato aaaa-mm-dd.")).build();
        } catch (Exception e) {
            LOG.errorf(e, "Falha na analise sindromica. chave=%s", chave);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Não foi possível calcular a análise por síndrome.")).build();
        }
    }

    /** Com JWT vale a unidade da sessao, nunca a informada; sem JWT (dev legado), a informada. */
    private String unidadeEfetiva(String informada) {
        String sessao = securityPermissionService.unidadeDaSessao(requestContext);
        String valor = sessao.isBlank() ? texto(informada) : sessao;
        return valor.split(":")[0].trim();
    }

    /** Alerta do cabecalho ligado por padrao; a unidade pode desligar (chave = sigla da sessao). */
    private boolean alertaCabecalhoAtivo(String unidade) {
        List<?> rows = em.createNativeQuery("SELECT alerta_cabecalho FROM vigilancia_epidemiologica_unidade WHERE unidade=:u")
                .setParameter("u", unidade).getResultList();
        return rows.isEmpty() || !Boolean.FALSE.equals(rows.get(0));
    }

    @GET @Path("configuracao/alerta-cabecalho")
    @Transactional
    public Response configuracaoAlertaCabecalho(@QueryParam("unidade") String unidade) {
        String chave = unidadeEfetiva(unidade);
        if (chave.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Unidade de trabalho nao identificada.")).build();
        }
        garantirEstrutura();
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("unidade", chave);
        resposta.put("ativo", alertaCabecalhoAtivo(chave));
        return Response.ok(resposta).build();
    }

    @PUT @Path("configuracao/alerta-cabecalho")
    @Transactional
    public Response salvarAlertaCabecalho(Map<String, Object> dto) {
        if (dto == null || dto.get("ativo") == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Informe se o alerta fica ativo.")).build();
        }
        String chave = unidadeEfetiva(texto(dto.get("unidade")));
        if (chave.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Unidade de trabalho nao identificada.")).build();
        }
        garantirEstrutura();
        boolean ativo = booleano(dto.get("ativo"), true);
        String usuario = texto(dto.get("usuario"));
        em.createNativeQuery("""
                INSERT INTO vigilancia_epidemiologica_unidade (unidade, alerta_cabecalho, atualizado_por, atualizado_em)
                VALUES (:u, :a, :p, now())
                ON CONFLICT (unidade) DO UPDATE SET alerta_cabecalho=EXCLUDED.alerta_cabecalho,
                    atualizado_por=EXCLUDED.atualizado_por, atualizado_em=now()
                """).setParameter("u", chave).setParameter("a", ativo).setParameter("p", usuario).executeUpdate();
        ALERTA_CACHE.remove(chave);
        LOG.infof("Alerta epidemiologico do cabecalho %s na unidade %s por %s", ativo ? "ligado" : "desligado", chave, usuario);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("unidade", chave);
        resposta.put("ativo", ativo);
        return Response.ok(resposta).build();
    }

    @GET @Path("configuracao")
    @Transactional
    public Response configuracao(@QueryParam("unidade") String unidade) {
        try {
            garantirEstrutura();
            List<Map<String, Object>> lista = regras(unidade);
            lista.forEach(regra -> regra.put("criterioCaso", DefinicaoCasoEpidemiologico.descricao(texto(regra.get("nome")), texto(regra.get("termos")))));
            return Response.ok(lista).build();
        } catch (Exception e) {
            LOG.error("Falha ao carregar configuracao da vigilancia epidemiologica", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("erro", "Nao foi possivel preparar a estrutura da vigilancia epidemiologica. Consulte o log do backend."))
                    .build();
        }
    }

    /** Lista da vigilancia: fica fora de "configuracao" para nao exigir a permissao de configurar regras. */
    @GET @Path("casos/{id}")
    @Transactional
    public Response casosDaVigilancia(@PathParam("id") int id) {
        return casos(id);
    }

    @GET @Path("configuracao/{id}/casos")
    @Transactional
    public Response casos(@PathParam("id") int id) {
        garantirEstrutura();
        List<Map<String, Object>> regra = regras("").stream().filter(item -> numero(item.get("id"), 0) == id).toList();
        if (regra.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
        Map<String, Object> r = regra.get(0); int janela = numero(r.get("janelaDias"), 7);
        // Lista pacientes: regra de outra unidade e recusada, e regra sem unidade so mostra a unidade da sessao.
        String unidadeSessao = unidadeEfetiva("");
        String unidadeRegra = texto(r.get("unidade")).split(":")[0].trim();
        if (!unidadeSessao.isBlank() && !unidadeRegra.isBlank() && !unidadeSessao.equalsIgnoreCase(unidadeRegra)) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Esta regra não pertence à unidade da sessão.")).build();
        }
        String unidadeConsulta = unidadeRegra.isBlank() ? unidadeSessao : texto(r.get("unidade"));
        CriterioCaso criterio = criterioCaso(List.of(r));
        String noTextoOriginal = criterio.predicado().replace("y.limpo", "x.txt");
        String sql = """
                SELECT DISTINCT ON (b.idbam)
                       b.idbam,p.idpaciente,p.nome,b.data,b.unidade,r.setor,r.queixaprincipal,t.diagnostico_provisorio,
                       a.anamnese,a.comentario,a.hpp,t.evolucao_prescricao,t.obs_enfermagem,ai.frente,ai.verso,
                       (""" + noTextoOriginal + " AND NOT " + criterio.predicado() + """
                       ) AS mencao_negada,
                       NOT """ + noTextoOriginal + """
                        AS sem_criterio, x.txt AS texto_completo
                FROM bam b
                JOIN pacientes p ON p.idpaciente=b.paciente_idpaciente
                JOIN registro r ON r.idregistro=b.registro_idregistro
                LEFT JOIN avaliacao a ON a.idavaliacao=b.avaliacao_idavaliacao
                LEFT JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
                LEFT JOIN anamneseinternacao ai ON ai.nbam=b.idbam
                CROSS JOIN LATERAL (SELECT """ + TEXTO_BAM_SQL + """
                 AS txt OFFSET 0) x
                CROSS JOIN LATERAL (SELECT CASE WHEN x.txt ILIKE ANY(CAST(string_to_array(:padroes,'|') AS text[]))
                    THEN REGEXP_REPLACE(x.txt, :negacao, ' ', 'g') ELSE '' END AS limpo OFFSET 0) y
                WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim
                  AND (:unidade='' OR b.unidade=:unidade)
                  AND x.txt ILIKE ANY(CAST(string_to_array(:padroes,'|') AS text[]))
                ORDER BY b.idbam, b.data DESC
                LIMIT 500
                """;
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("inicio", LocalDate.now().minusDays(janela - 1))
                .setParameter("fim", LocalDate.now())
                .setParameter("unidade", unidadeConsulta)
                .setParameter("acentos", ACENTOS)
                .setParameter("semAcentos", SEM_ACENTOS)
                .setParameter("padroes", criterio.padroesCitados())
                .setParameter("negacao", criterio.negacao())
                .getResultList();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object[] x : rows) {
            Map<String,Object> m = new LinkedHashMap<>();
            String[] k={"bam","idpaciente","paciente","data","unidade","setor","queixaPrincipal","diagnostico","anamneseTriagem","comentarioTriagem","hpp","evolucaoMedica","observacaoEnfermagem","anamneseMedicaFrente","anamneseMedicaVerso","mencaoNegada","semCriterio"};
            for(int i=0;i<k.length;i++)m.put(k[i],x[i]);
            // O texto buscado tem mais campos que os informes da tela: mostra o que foi encontrado e de onde veio.
            List<String> encontrados = DefinicaoCasoEpidemiologico.termosNoTexto(texto(x[k.length]),
                    DefinicaoCasoEpidemiologico.termosCitados(texto(r.get("nome")), texto(r.get("termos"))));
            m.put("termosEncontrados", encontrados);
            m.put("trechosEncontrados", DefinicaoCasoEpidemiologico.trechosDoTexto(texto(x[k.length]), encontrados, 70, 4));
            out.add(m);
        }
        anexarClassificacoes(id, out);
        anexarEvidencias(r, out);
        // Rastreabilidade do acesso a dado identificado; o log nao leva dado do paciente.
        var usuario = securityPermissionService.resolve(requestContext);
        LOG.infof("Casos da regra consultados: regra=%d, unidade=%s, matricula=%s, atendimentos=%d", id, unidadeConsulta,
                usuario == null ? "" : usuario.matricula(), out.size());
        return Response.ok(out).build();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_FICHA = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Laudo e ficha SINAN de cada BAM viram evidencia e, se seguro, sugestao. Nada e gravado; a ficha nao sai do servidor. */
    private void anexarEvidencias(Map<String, Object> regra, List<Map<String, Object>> casos) {
        if (casos.isEmpty()) return;
        String nome = texto(regra.get("nome"));
        String termos = texto(regra.get("termos"));
        String bams = casos.stream().map(caso -> texto(caso.get("bam"))).distinct().collect(java.util.stream.Collectors.joining(",", "{", "}"));
        Map<String, List<EvidenciaCasoEpidemiologico.Evidencia>> porBam = new LinkedHashMap<>();
        // O arquivo do laudo fica de fora: pode ser base64 grande, e a tela so precisa saber que existe.
        @SuppressWarnings("unchecked") List<Object[]> laudos = em.createNativeQuery("""
                SELECT p.bam, p.descricao, p.grupopedidos, CAST(NULL AS varchar), i.nome_exame, i.valor
                FROM pedidoresultadolaboratorio p
                LEFT JOIN pedidoresultadolaboratorio_item i ON i.idpedidoresultado = p.idpedidoresultado
                WHERE p.bam = ANY(CAST(:bams AS int[]))
                """).setParameter("bams", bams).getResultList();
        for (Object[] laudo : laudos) {
            boolean temItem = laudo[4] != null;
            String exame = temItem ? texto(laudo[4]) : texto(laudo[1]) + " " + texto(laudo[2]);
            // Sem item estruturado, o valor pode ser de outro exame do mesmo pedido: nunca vira "reagente", so "ver laudo".
            String valor = temItem ? texto(laudo[5]) : "";
            EvidenciaCasoEpidemiologico.Evidencia evidencia = EvidenciaCasoEpidemiologico.doExame(temItem ? exame : resumoExame(exame), valor, nome, termos);
            if (evidencia != null) porBam.computeIfAbsent(texto(laudo[0]), bam -> new ArrayList<>()).add(evidencia);
        }
        // Desligado ate a leitura do PDF ser conferida com laudos reais: captura, mas nao influencia a vigilancia.
        if (sugestoesLaudoPdf) {
            @SuppressWarnings("unchecked") List<Object[]> lidos = em.createNativeQuery(
                    "SELECT bam, nome_exame, resultado FROM laboratorio_resultado_infeccioso WHERE bam = ANY(CAST(:bams AS int[]))")
                    .setParameter("bams", bams).getResultList();
            for (Object[] lido : lidos) {
                String valor = "REAGENTE".equals(texto(lido[2])) ? "Reagente" : "Não reagente";
                EvidenciaCasoEpidemiologico.Evidencia evidencia = EvidenciaCasoEpidemiologico.doExame(texto(lido[1]), valor, nome, termos);
                if (evidencia != null) porBam.computeIfAbsent(texto(lido[0]), bam -> new ArrayList<>()).add(evidencia);
            }
        }
        @SuppressWarnings("unchecked") List<Object[]> fichas = em.createNativeQuery("""
                SELECT idbam, tipo, dados FROM notificacao_sinan
                WHERE idbam = ANY(CAST(:bams AS int[])) AND status <> 'RASCUNHO'
                """).setParameter("bams", bams).getResultList();
        for (Object[] ficha : fichas) {
            try {
                @SuppressWarnings("unchecked") Map<String, Object> dados = JSON_FICHA.readValue(texto(ficha[2]), Map.class);
                EvidenciaCasoEpidemiologico.Evidencia evidencia = EvidenciaCasoEpidemiologico.daFichaSinan(texto(ficha[1]), dados, nome, termos);
                if (evidencia != null) porBam.computeIfAbsent(texto(ficha[0]), bam -> new ArrayList<>()).add(evidencia);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // A mensagem da excecao pode citar trecho da ficha: registra so o BAM.
                LOG.warnf("Ficha SINAN com dados ilegiveis ignorada na vigilancia: bam=%s", texto(ficha[0]));
            }
        }
        for (Map<String, Object> caso : casos) {
            List<EvidenciaCasoEpidemiologico.Evidencia> doCaso = porBam.getOrDefault(texto(caso.get("bam")), List.of());
            if (doCaso.isEmpty()) continue;
            caso.put("evidencias", doCaso.stream().map(EvidenciaCasoEpidemiologico::paraTela).toList());
            EvidenciaCasoEpidemiologico.Sugestao sugestao = EvidenciaCasoEpidemiologico.sugerir(doCaso);
            if (sugestao != null) caso.put("sugestao", EvidenciaCasoEpidemiologico.paraTela(sugestao));
        }
    }

    /** grupopedidos pode ser longo: a evidencia mostra so o inicio do que foi pedido. */
    private static String resumoExame(String pedido) {
        String limpo = pedido.replaceAll("\\s+", " ").trim();
        return limpo.length() > 120 ? limpo.substring(0, 120) + "..." : limpo;
    }

    /** Ultima decisao de cada BAM nesta regra; sem registro o caso segue suspeito. */
    private void anexarClassificacoes(int regraId, List<Map<String, Object>> casos) {
        @SuppressWarnings("unchecked") List<Object[]> rows = !tabelaClassificacaoExiste() ? List.of() : em.createNativeQuery("""
                SELECT DISTINCT ON (idbam) idbam, classificacao, criterio, justificativa, profissional, criado_em
                FROM vigilancia_caso_classificacao WHERE regra_id=:regra ORDER BY idbam, id DESC
                """).setParameter("regra", regraId).getResultList();
        Map<String, Object[]> porBam = new LinkedHashMap<>();
        rows.forEach(row -> porBam.put(texto(row[0]), row));
        for (Map<String, Object> caso : casos) {
            Object[] row = porBam.get(texto(caso.get("bam")));
            caso.put("classificacao", row == null ? ClassificacaoCasoEpidemiologico.SUSPEITO : row[1]);
            if (row == null) continue;
            caso.put("classificacaoCriterio", row[2]);
            caso.put("classificacaoJustificativa", row[3]);
            caso.put("classificacaoProfissional", row[4]);
            caso.put("classificacaoEm", texto(row[5]));
        }
    }

    /** Grava uma nova linha a cada decisao: a anterior fica como historico. Nao altera o prontuario. */
    @POST @Path("casos/{regraId}/{bam}/classificacao") @Transactional
    public Response classificarCaso(@PathParam("regraId") int regraId, @PathParam("bam") int bam, Map<String, Object> dto) {
        garantirEstrutura();
        String classificacao = texto(dto == null ? null : dto.get("classificacao")).toUpperCase(Locale.ROOT);
        String criterio = texto(dto == null ? null : dto.get("criterio")).toUpperCase(Locale.ROOT);
        String justificativa = texto(dto == null ? null : dto.get("justificativa"));
        String invalida = ClassificacaoCasoEpidemiologico.validar(classificacao, criterio, justificativa);
        if (invalida != null) return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", invalida)).build();
        if (!tabelaClassificacaoExiste()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("erro", "A classificação de casos ainda não está disponível. Confirme a aplicação da migration V240.")).build();
        }
        var usuario = securityPermissionService.resolve(requestContext);
        if (usuario == null || texto(usuario.matricula()).isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("erro", "Profissional não identificado na sessão.")).build();
        }
        Map<String, Object> regra = regras("").stream().filter(item -> numero(item.get("id"), 0) == regraId).findFirst().orElse(null);
        if (regra == null) return Response.status(Response.Status.NOT_FOUND).entity(Map.of("erro", "Regra não encontrada.")).build();
        @SuppressWarnings("unchecked") List<Object[]> atendimento = em.createNativeQuery("SELECT unidade, paciente_idpaciente FROM bam WHERE idbam=:bam")
                .setParameter("bam", bam).getResultList();
        if (atendimento.isEmpty()) return Response.status(Response.Status.NOT_FOUND).entity(Map.of("erro", "BAM não encontrado.")).build();
        String unidade = unidadeEfetiva(texto(regra.get("unidade")));
        String unidadeBam = texto(atendimento.get(0)[0]);
        if (!unidade.equalsIgnoreCase(unidadeBam)) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Este BAM não pertence à unidade da sessão.")).build();
        }
        em.createNativeQuery("""
                INSERT INTO vigilancia_caso_classificacao
                    (regra_id, doenca, unidade, idbam, idpaciente, classificacao, criterio, justificativa, matricula, profissional)
                VALUES (:regra, :doenca, :unidade, :bam, :paciente, :classificacao, :criterio, :justificativa, :matricula, :profissional)
                """)
                .setParameter("regra", regraId).setParameter("doenca", texto(regra.get("nome"))).setParameter("unidade", unidadeBam)
                .setParameter("bam", bam).setParameter("paciente", atendimento.get(0)[1]).setParameter("classificacao", classificacao)
                .setParameter("criterio", criterio).setParameter("justificativa", justificativa)
                .setParameter("matricula", usuario.matricula()).setParameter("profissional", texto(usuario.nome()))
                .executeUpdate();
        limparCaches();
        LOG.infof("Caso epidemiologico classificado: regra=%d, bam=%d, classificacao=%s, matricula=%s", regraId, bam, classificacao, usuario.matricula());
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("classificacao", classificacao);
        resposta.put("classificacaoCriterio", criterio);
        resposta.put("classificacaoJustificativa", justificativa);
        resposta.put("classificacaoProfissional", texto(usuario.nome()));
        return Response.status(Response.Status.CREATED).entity(resposta).build();
    }

    private String motivoDoSinal(AvaliacaoSinalEpidemiologico.Resultado r, boolean ocorrenciaAberta) {
        return switch (r.estado()) {
            case CASO_RARO -> "Agravo raro ou grave: um caso já justifica investigar.";
            case SUBINDO -> r.compativelComEpoca()
                    ? "Acima das últimas semanas, mas dentro do esperado para a época do ano."
                    : String.format("Acima do esperado para as últimas semanas (esperado: %.1f).", r.esperadoRecente());
            case ACIMA_DA_EPOCA -> String.format("Estável nas últimas semanas, mas acima da mesma época de anos anteriores (esperado: %.1f).", r.esperadoEpoca());
            case EM_CURSO -> "Em curso: os casos seguem no patamar do alerta e a ocorrência fica aberta até alguém encerrar.";
            case REVISADO -> "Acima do esperado, mas a ocorrência já foi encerrada ou descartada nesta janela.";
            case NORMAL -> ocorrenciaAberta ? "Dentro do esperado, com ocorrência ainda aberta para encerrar." : "Dentro do esperado.";
        };
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
        Map<String, Object> atual;
        try {
            atual = ocorrenciaSistemaService.dados(id);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("erro", "Ocorrência não encontrada.")).build();
        }
        String sessao = unidadeEfetiva("");
        // "TODAS" = ocorrencia que perdeu a unidade no bug antigo de atualizacao parcial; precisa poder ser encerrada.
        if (!sessao.isBlank() && !"TODAS".equalsIgnoreCase(texto(atual.get("unidade"))) && !sessao.equalsIgnoreCase(texto(atual.get("unidade")))) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("erro", "Esta ocorrência não pertence à unidade da sessão.")).build();
        }
        var usuario = securityPermissionService.resolve(requestContext);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("usuario", usuario == null ? texto(dto == null ? null : dto.get("usuario")) : texto(usuario.nome()));
        patch.put("observacao", "Investigacao " + acao + ". Justificativa: " + justificativa + ". Medida: " + medida);
        if ("assumir".equals(acao)) { patch.put("status", "analise"); patch.put("responsavel", texto(dto == null ? null : dto.get("responsavel"))); }
        if ("confirmar".equals(acao)) patch.put("status", "analise");
        // Descartar tambem encerra: senao o sinal ficaria "em curso" para sempre.
        if ("encerrar".equals(acao) || "descartar".equals(acao)) patch.put("status", "resolvida");
        Map<String, Object> atualizada = ocorrenciaSistemaService.atualizarCampos(id, patch);
        limparCaches();
        return Response.ok(atualizada).build();
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
     * Conta pacientes (uma vez cada) por regra numa unica consulta: o texto de cada
     * atendimento e montado e limpo uma vez e testado contra a definicao de cada regra.
     * Devolve {casos, so com mencao negada, citam termos sem cumprir a definicao}, na ordem
     * das regras. O G-PEC usa problema estruturado: conta o agravo registrado pelo profissional.
     */
    private long[][] contarPorRegra(String unidade, List<Map<String, Object>> regras, LocalDate inicio, LocalDate fim, boolean gpec) {
        if (regras.isEmpty()) return new long[0][3];
        String fonte = gpec ? "GPEC" : "GHBAM";
        return contarDasLinhas(varrer(regras, inicio, fim, unidade, fonte), regras.size(), inicio, fim, fonte);
    }

    private record ContagemRegra(long atual, long base, long negadas, long semCriterio, Double mediaEpoca, double ajusteRecente,
                                 double ajusteEpoca, long confirmados, long provaveis, long descartados) {}
    private static final ContagemRegra CONTAGEM_VAZIA = new ContagemRegra(0, 0, 0, 0, null, 1d, 1d, 0, 0, 0);
    private static final int ANOS_EPOCA = 2;
    // Janela de meio ano ou mais ja cobre a sazonalidade e encareceria a varredura.
    private static final int JANELA_MAXIMA_EPOCA = 180;

    /** Contagens por regra, ja com a decisao humana por caso; uma varredura por janela distinta, nao por regra. */
    private Map<Map<String, Object>, ContagemRegra> contarPorJanela(String unidade, List<Map<String, Object>> regras) {
        Map<Map<String, Object>, ContagemRegra> resultado = new java.util.IdentityHashMap<>();
        Map<Integer, List<Map<String, Object>>> porJanela = new LinkedHashMap<>();
        for (Map<String, Object> regra : regras) {
            resultado.put(regra, CONTAGEM_VAZIA);
            if (texto(regra.get("termos")).isBlank()) continue;
            porJanela.computeIfAbsent(numero(regra.get("janelaDias"), 7), j -> new ArrayList<>()).add(regra);
        }
        Map<Integer, Map<String, String>> classificacoes = classificacoesDasRegras(regras);
        LocalDate hoje = LocalDate.now();
        porJanela.forEach((janela, grupo) -> {
            List<Map<String, String>> porRegra = grupo.stream()
                    .map(regra -> classificacoes.getOrDefault(numero(regra.get("id"), 0), Map.<String, String>of())).toList();
            LocalDate inicioAtual = hoje.minusDays(janela - 1);
            List<Object[]> linhasAtuais = varrer(grupo, inicioAtual, hoje, unidade, "GHBAM");
            long[][] resumo = ClassificacaoCasoEpidemiologico.aplicar(linhasAtuais, porRegra);
            long[][] atual = contarDasLinhas(linhasAtuais, grupo.size(), inicioAtual, hoje, "GHBAM");
            LocalDate inicioBase = hoje.minusDays(janela * 5L);
            LocalDate fimBase = hoje.minusDays(janela);
            long[][] base = contarComClassificacao(grupo, porRegra, inicioBase, fimBase, unidade);
            double atendimentosAtuais = atendimentos(unidade, inicioAtual, hoje);
            double ajusteRecente = AvaliacaoSinalEpidemiologico.ajustePorAtendimentos(atendimentosAtuais, atendimentos(unidade, inicioBase, fimBase) / 4d);
            // Ano sem nenhum atendimento na unidade e falta de historico, nao "zero casos".
            long[] casosEpoca = new long[grupo.size()];
            long atendimentosEpoca = 0;
            int anosComDado = 0;
            for (int ano = 1; janela < JANELA_MAXIMA_EPOCA && ano <= ANOS_EPOCA; ano++) {
                LocalDate inicio = inicioAtual.minusYears(ano);
                LocalDate fim = hoje.minusYears(ano);
                long noAno = atendimentos(unidade, inicio, fim);
                if (noAno == 0) continue;
                long[][] contagem = contarComClassificacao(grupo, porRegra, inicio, fim, unidade);
                for (int i = 0; i < grupo.size(); i++) casosEpoca[i] += contagem[i][0];
                atendimentosEpoca += noAno;
                anosComDado++;
            }
            double ajusteEpoca = anosComDado == 0 ? 1d
                    : AvaliacaoSinalEpidemiologico.ajustePorAtendimentos(atendimentosAtuais, (double) atendimentosEpoca / anosComDado);
            for (int i = 0; i < grupo.size(); i++) {
                resultado.put(grupo.get(i), new ContagemRegra(atual[i][0], base[i][0], atual[i][1], atual[i][2],
                        anosComDado == 0 ? null : (double) casosEpoca[i] / anosComDado, ajusteRecente, ajusteEpoca,
                        resumo[i][0], resumo[i][1], resumo[i][2]));
            }
        });
        return resultado;
    }

    private long[][] contarComClassificacao(List<Map<String, Object>> grupo, List<Map<String, String>> porRegra, LocalDate inicio, LocalDate fim, String unidade) {
        List<Object[]> linhas = varrer(grupo, inicio, fim, unidade, "GHBAM");
        ClassificacaoCasoEpidemiologico.aplicar(linhas, porRegra);
        return contarDasLinhas(linhas, grupo.size(), inicio, fim, "GHBAM");
    }

    private long atendimentos(String unidade, LocalDate inicio, LocalDate fim) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM bam b WHERE CAST(b.data AS date) BETWEEN :inicio AND :fim AND (:unidade='' OR b.unidade=:unidade)")
                .setParameter("inicio", inicio).setParameter("fim", fim).setParameter("unidade", texto(unidade))
                .getSingleResult()).longValue();
    }

    /** BAMs descartados cujo diagnostico cita a doenca da regra, como literal de array do PostgreSQL. */
    private String bamsDescartadosDosAcumulos(String unidade, LocalDate inicio, LocalDate fim) {
        if (!tabelaClassificacaoExiste()) return "{}";
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("""
                SELECT * FROM (
                    SELECT DISTINCT ON (c.regra_id, c.idbam) c.regra_id, c.idbam, c.classificacao, t.diagnostico_provisorio
                    FROM vigilancia_caso_classificacao c
                    JOIN bam b ON b.idbam = c.idbam
                    LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                    WHERE b.unidade = :unidade AND CAST(b.data AS date) BETWEEN :inicio AND :fim
                    ORDER BY c.regra_id, c.idbam, c.id DESC
                ) ultima WHERE classificacao = 'DESCARTADO'
                """).setParameter("unidade", unidade).setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
        if (rows.isEmpty()) return "{}";
        Map<Integer, Map<String, Object>> regrasPorId = new LinkedHashMap<>();
        regras(unidade).forEach(regra -> regrasPorId.put(numero(regra.get("id"), 0), regra));
        java.util.Set<String> bams = new java.util.LinkedHashSet<>();
        for (Object[] row : rows) {
            Map<String, Object> regra = regrasPorId.get(((Number) row[0]).intValue());
            if (regra != null && DefinicaoCasoEpidemiologico.diagnosticoCitaRegra(texto(row[3]), texto(regra.get("nome")), texto(regra.get("termos")))) {
                bams.add(String.valueOf(((Number) row[1]).intValue()));
            }
        }
        return "{" + String.join(",", bams) + "}";
    }

    private static volatile boolean tabelaClassificacaoPronta = false;

    /** Sem a V240 o sinal e o mapa seguem pela leitura do texto: consulta com erro abortaria a transacao inteira. */
    private boolean tabelaClassificacaoExiste() {
        if (tabelaClassificacaoPronta) return true;
        tabelaClassificacaoPronta = Boolean.TRUE.equals(em.createNativeQuery("SELECT to_regclass('vigilancia_caso_classificacao') IS NOT NULL").getSingleResult());
        return tabelaClassificacaoPronta;
    }

    /** Classificacoes na ordem das regras; regra do catalogo padrao ("padrao-N") nao tem id numerico nem classificacao. */
    private List<Map<String, String>> classificacoesPorIndice(List<Map<String, Object>> regras) {
        Map<Integer, Map<String, String>> porRegra = classificacoesDasRegras(regras);
        return regras.stream().map(regra -> porRegra.getOrDefault(numero(regra.get("id"), 0), Map.<String, String>of())).toList();
    }

    /** regra -> BAM -> classificacao mais recente. */
    private Map<Integer, Map<String, String>> classificacoesDasRegras(List<Map<String, Object>> regras) {
        Map<Integer, Map<String, String>> out = new LinkedHashMap<>();
        List<Integer> ids = regras.stream().map(regra -> numero(regra.get("id"), 0)).filter(id -> id > 0).toList();
        if (ids.isEmpty() || !tabelaClassificacaoExiste()) return out;
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("""
                SELECT DISTINCT ON (regra_id, idbam) regra_id, idbam, classificacao
                FROM vigilancia_caso_classificacao WHERE regra_id IN (:ids) ORDER BY regra_id, idbam, id DESC
                """).setParameter("ids", ids).getResultList();
        for (Object[] row : rows) {
            out.computeIfAbsent(((Number) row[0]).intValue(), id -> new LinkedHashMap<>()).put(texto(row[1]), texto(row[2]));
        }
        return out;
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
            em.createNativeQuery("""
                    CREATE TABLE IF NOT EXISTS vigilancia_epidemiologica_unidade (
                        unidade VARCHAR(30) PRIMARY KEY, alerta_cabecalho BOOLEAN NOT NULL DEFAULT TRUE,
                        atualizado_por VARCHAR(120) NOT NULL DEFAULT '', atualizado_em TIMESTAMP NOT NULL DEFAULT now()
                    )
                    """).executeUpdate();
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
        {"Hantavirose", "hantavirose, hantavirus, hanta virus"},
        {"Sífilis", "sifilis"},
        {"Atendimento antirrábico", "mordedura, antirrabica, antirrabico, agressao por animal"},
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
        List<String> padroes = new ArrayList<>();
        for (String termo : termosNormalizados(termos)) padroes.add("% " + termo + " %");
        return String.join("|", padroes);
    }
    /** Termos como aparecem no texto normalizado: sem acento, minusculos, so letras, digitos e um espaco. */
    static List<String> termosNormalizados(String termos) {
        String semAcento = Normalizer.normalize(termos == null ? "" : termos, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        List<String> out = new ArrayList<>();
        for (String parte : semAcento.toLowerCase(Locale.ROOT).split(",")) {
            String normalizado = parte.replaceAll("[^a-z0-9]+", " ").trim();
            if (!normalizado.isBlank() && !out.contains(normalizado)) out.add(normalizado);
        }
        return out;
    }
    /**
     * Regex (PostgreSQL e Java) das mencoes negadas dos termos: "nega febre", "sem sinais de
     * sarampo", "mpox descartado". O que for "nao descartado" ou "descartar" continua contando.
     * Lista de negacao ("nega tosse e febre") nao e reconhecida e tambem continua contando:
     * na duvida o atendimento fica no sinal, porque a CCIH confere; caso escondido ninguem ve.
     */
    static String regexMencaoNegada(String termos) {
        List<String> lista = termosNormalizados(termos);
        if (lista.isEmpty()) return "^$"; // o texto normalizado nunca e vazio: nao remove nada
        lista.sort((a, b) -> b.length() - a.length());
        String t = String.join("|", lista);
        return "(?<! nao)(?<! nao se)(?<! nao foi) (?:"
                + "(?:nega|negam|negou|nego|negando|sem|ausencia de|nao ha|nao apresenta|nao tem|nao possui"
                + "|descartado|descartada|descarta|afastado|afastada|afasta|excluido|excluida|exclui) "
                + "(?:(?:de|da|do|sinais de|sintomas de|relato de|historia de|quadro de|suspeita de|hipotese de) )?(?:" + t + ")"
                + "|(?:" + t + ") (?:descartad[oa]|afastad[oa]|excluid[oa]|negativ[oa])"
                + ")(?= )";
    }
    /** Minimo e fator do sinal; regra cadastrada usa os seus, catalogo padrao usa o minimo geral. */
    private record LimitesRegra(int minimo, double fator, int pesoAlto, boolean cadastrada, int minimoRegiao) {}
    // Regiao com menos casos nao vira circulo em doenca comum; os casos seguem no total e no aviso.
    private static final int MINIMO_REGIAO = 3;
    private LimitesRegra limitesDe(Map<String,Object> regra) {
        boolean cadastrada = regra != null && regra.containsKey("minimoCasos");
        double fator = cadastrada ? decimal(regra.get("fatorAlerta"), 2d) : 2d;
        if (regra != null && DefinicaoCasoEpidemiologico.casoUnicoImporta(texto(regra.get("nome")), texto(regra.get("termos")))) {
            return new LimitesRegra(1, fator, 2, cadastrada, 1);
        }
        if (cadastrada) {
            int minimo = numero(regra.get("minimoCasos"), 3);
            return new LimitesRegra(minimo, fator, minimo * 2, true, Math.min(MINIMO_REGIAO, minimo));
        }
        return new LimitesRegra(MINIMO_INVESTIGACAO + 1, fator, MINIMO_INVESTIGACAO * 2 + 1, false, MINIMO_REGIAO);
    }
    private LimitesRegra limitesDaRegra(String unidade, String termos) {
        if (termos.isBlank()) return limitesDe(null);
        return limitesDe(regrasMapa(unidade).stream()
                .filter(regra -> Boolean.TRUE.equals(regra.get("ativo")) && texto(regra.get("termos")).equals(termos))
                .findFirst().orElse(null));
    }
    private String texto(Object v){return v==null?"":String.valueOf(v).trim();} private int numero(Object v,int p){try{return Math.max(1,Integer.parseInt(texto(v)));}catch(Exception e){return p;}} private double decimal(Object v,double p){try{return Math.max(1d,Double.parseDouble(texto(v)));}catch(Exception e){return p;}} private boolean booleano(Object v,boolean p){return v==null?p:Boolean.parseBoolean(texto(v));}
}
