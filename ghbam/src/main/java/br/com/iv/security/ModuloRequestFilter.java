package br.com.iv.security;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Trava de módulo contratado por unidade (tabela unidade_modulo, tela /base/unidades → Habilitar módulos).
 *
 * Independe do RBAC: módulo desligado vale para todos, inclusive kadm. Começa em modo sombra
 * (ghbam.modulos.enforce=false): só registra "MODULO-SHADOW" em logs/rbac-shadow.log o que bloquearia.
 * Análise: grep "MODULO-SHADOW" logs/rbac-shadow.log*  — antes de ligar, confira que nenhuma tela
 * do núcleo (histórico, PEP, ⌘K, Kanban) aparece chamando endpoint de módulo desligado.
 *
 * Só entram aqui prefixos que são claramente de um módulo; leituras compartilhadas (pacientes,
 * historicoPaciente, prontuario, bam/situacaoatual...) ficam no núcleo e nunca são travadas.
 * Mapa: docs/modulos-contratacao.md (frontend), seção 2.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class ModuloRequestFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(ModuloRequestFilter.class.getName());
    private static final long CACHE_MS = 30_000;

    static final Map<String, String> PREFIXOS = new LinkedHashMap<>();
    static {
        modulo("PA", "bam", "registro", "tratamento", "avaliacao", "atendimentomedico", "chamada", "limparpainel",
                "jornada-pa", "fluxo-pacientes", "conformidade-pendencia", "configuracao-conformidade-sla",
                "prescricao", "prescricao-itens", "prescricaocontinuacao", "modeloprescricao", "modeloevolucao",
                "receitas", "resumomedico", "evolucaopacientereceituario", "parecerespecialista",
                "consultapediatrica", "vacinapediatrica");
        modulo("INTERNACAO", "novointernado", "internacao", "localcenso", "setorcenso", "leitoreserva",
                "laudoautorizacao", "anamneseinternacao", "evolucaopaciente", "evolucaopacienteenf",
                "anotacaoenfermagem", "sae-enfermagem", "modeloevolucaoenf", "modeloprescricaoenf",
                "backend_balanco_hidrico_completo", "backend_controle_entrada", "backend_controle_saida",
                "backend_resumo_balanco", "escalapaciente", "resumodealta", "resumo-alta", "sumario-obito",
                "cautela", "visitantes", "controle-assistencial", "documentos-assistenciais");
        modulo("MULTI", "atendimentomultidisciplinar", "multidisciplinarencaminhamento", "multidisciplinarpts");
        modulo("CIRURGIA", "centro-cirurgico", "listaanestesica");
        modulo("CME", "cme");
        modulo("LAB", "pedidoResultadoLaboratorio");
        modulo("IMAGEM", "imagens", "api/pacs");
        modulo("FARMACIA", "pharmacia", "estoquecentral", "zamed");
        modulo("ALMOX", "almoxarifado");
        modulo("FATURAMENTO", "afaturar", "tiss", "procedimentossus", "procedimentosPasrv");
        modulo("FATURAMENTO_SUS", "faturamento", "sigtap", "consulta-cns");
        modulo("CONSULTORIO", "atendimentomedicoagenda", "configuracaoagendaconsultorio", "consultorioagenda",
                "financeiroconsultorio", "procedimentoconsultorio");
        modulo("ODONTO", "api/odontologia");
        modulo("APS", "api/aps", "historicopaciente/aps", "push/pareamento");
        modulo("VIGILANCIA", "vigilancia-epidemiologica", "notificacoes-sinan", "api/violencias");
        modulo("QUALIDADE", "nsp");
        modulo("PROTOCOLOS", "protocolos", "protocolos-execucao", "protocolos-fluxograma", "classificacao-ghbam",
                "classificacao-ghbam-plano");
        modulo("RH", "rh", "auth/ponto");
        modulo("GED", "ged");
        modulo("BI", "bi-executivo", "producao-medica", "producao-assistencial", "monitoramento");
        modulo("IA", "api/ia", "api/hf", "genai", "api/rag", "rag2", "resumo-clinico", "analise-risco", "ditado",
                "api/assistant", "imagem-ia");
        modulo("INTEROP", "interoperabilidade", "integracao/pacientes");
        modulo("CHAT", "api/chat");
        modulo("PAINEL_MIDIA", "unidadeVideoConfig", "unidadeVideos", "videoProxy", "video", "listasM3u", "painelVoz");
        modulo("PORTAL", "portal-paciente");
        modulo("BIOMETRIA", "api/fingerprint");
    }

    private static void modulo(String codigo, String... prefixos) {
        for (String p : prefixos) PREFIXOS.put(p.toLowerCase(Locale.ROOT), codigo);
    }

    private record Cache(long em, Set<String> desligados) {}
    private static final Map<String, Cache> CACHE = new ConcurrentHashMap<>();

    /** Chamado ao salvar os módulos de uma unidade. */
    public static void invalidar(String sigla) {
        if (sigla != null) CACHE.remove(sigla.trim().toUpperCase(Locale.ROOT));
    }

    @ConfigProperty(name = "ghbam.modulos.enforce", defaultValue = "false")
    boolean enforce;

    @Inject EntityManager em;
    @Inject SecurityPermissionService securityPermissionService;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ctx.getMethod())) return;
        String modulo;
        String sigla;
        try {
            modulo = moduloDoCaminho(ctx.getUriInfo().getPath());
            if (modulo == null) return;
            sigla = siglaDaRequisicao(ctx);
            if (sigla.isEmpty() || !desligados(sigla).contains(modulo)) return;
        } catch (RuntimeException e) {
            // A trava de módulo nunca derruba a requisição por falha própria (tabela ausente, banco lento...).
            LOGGER.fine("MODULO-SHADOW falha ao avaliar: " + e.getMessage());
            return;
        }
        String caminho = ctx.getUriInfo().getPath();
        if (!enforce) {
            LOGGER.warning("MODULO-SHADOW decision=denied modulo=" + modulo + " unidade=" + sigla
                    + " method=" + ctx.getMethod() + " path=" + caminho);
            return;
        }
        LOGGER.warning("MODULO-ENFORCE decision=denied modulo=" + modulo + " unidade=" + sigla
                + " method=" + ctx.getMethod() + " path=" + caminho);
        ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("motivo", "MODULO_NAO_CONTRATADO", "modulo", modulo,
                        "mensagem", "Este módulo não está habilitado para a unidade."))
                .build());
    }

    /** Módulo dono do caminho (prefixo mais longo, com limite em "/"), ou null se for do núcleo. */
    static String moduloDoCaminho(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT).replaceAll("^/+", "").replaceAll("/+$", "");
        if (p.startsWith("unidade-modulos")) return null;
        String achado = null;
        int tamanho = -1;
        for (Map.Entry<String, String> e : PREFIXOS.entrySet()) {
            String prefixo = e.getKey();
            if ((p.equals(prefixo) || p.startsWith(prefixo + "/")) && prefixo.length() > tamanho) {
                achado = e.getValue();
                tamanho = prefixo.length();
            }
        }
        return achado;
    }

    /** Unidade da sessão (JWT); rotas públicas (painéis, portal) mandam ?unidade=. */
    private String siglaDaRequisicao(ContainerRequestContext ctx) {
        String unidade = "";
        try {
            unidade = securityPermissionService.unidadeDaSessao(ctx);
        } catch (RuntimeException ignorado) {
            // Sem token válido: tenta o parâmetro abaixo.
        }
        if (unidade == null || unidade.isBlank()) {
            List<String> param = ctx.getUriInfo().getQueryParameters().get("unidade");
            unidade = param == null || param.isEmpty() ? "" : param.get(0);
        }
        return unidade == null ? "" : unidade.split(":")[0].trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> desligados(String sigla) {
        long agora = System.currentTimeMillis();
        Cache c = CACHE.get(sigla);
        if (c != null && agora - c.em() < CACHE_MS) return c.desligados();
        @SuppressWarnings("unchecked")
        List<Object> linhas = em.createNativeQuery("SELECT modulo FROM unidade_modulo WHERE sigla = ?1 AND ativo = FALSE")
                .setParameter(1, sigla)
                .getResultList();
        Set<String> set = new HashSet<>();
        linhas.forEach(l -> set.add(String.valueOf(l)));
        set.removeAll(br.com.iv.controller.UnidadeModuloController.FIXOS);
        CACHE.put(sigla, new Cache(agora, set));
        return set;
    }
}
