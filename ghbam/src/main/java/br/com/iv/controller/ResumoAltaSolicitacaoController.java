package br.com.iv.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Resumo de Alta como alta medica: o NIR consulta se o BAM ja tem resumo assinado e,
 * quando nao tem, pede o preenchimento. O pedido fica em resumo_alta_solicitacao ate
 * qualquer medico assinar o RESUMO_ALTA daquele BAM (fechado em AssinaturaClinicaService).
 */
@Path("resumo-alta")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ResumoAltaSolicitacaoController {

    @Inject
    EntityManager em;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Context
    ContainerRequestContext requestContext;

    @ConfigProperty(name = "ghbam.alta.exigir-resumo-assinado", defaultValue = "false")
    boolean exigirResumoAssinado;

    public static class SolicitacaoRequest {
        public String motivoSaida;
    }

    @GET
    @Path("/bam/{idbam}/status")
    public Response status(@PathParam("idbam") int idbam) {
        return Response.ok(montarStatus(idbam)).build();
    }

    @POST
    @Path("/bam/{idbam}/solicitar")
    @Transactional
    public Response solicitar(@PathParam("idbam") int idbam, SolicitacaoRequest request) {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<?> unidadeBam = em.createNativeQuery("SELECT unidade FROM bam WHERE idbam = :id")
                .setParameter("id", idbam).getResultList();
        if (unidadeBam.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("erro", "BAM nao encontrado.")).build();
        }
        String unidade = unidadeBam.get(0) == null ? normalizarUnidade(user.unidade()) : String.valueOf(unidadeBam.get(0));
        String motivo = request == null || request.motivoSaida == null ? "" : request.motivoSaida.trim();
        if (motivo.length() > 60) {
            motivo = motivo.substring(0, 60);
        }
        // Ja assinado: nada a pedir. Ja pedido e aberto: o indice parcial unico evita duplicar.
        if (!resumoAssinado(idbam)) {
            em.createNativeQuery("INSERT INTO resumo_alta_solicitacao (idbam, unidade, solicitado_por, solicitado_matricula, motivo_saida) "
                    + "VALUES (:idbam, :unidade, :nome, :matricula, :motivo) ON CONFLICT DO NOTHING")
                    .setParameter("idbam", idbam)
                    .setParameter("unidade", unidade)
                    .setParameter("nome", user.nome())
                    .setParameter("matricula", user.matricula())
                    .setParameter("motivo", motivo)
                    .executeUpdate();
        }
        return Response.ok(montarStatus(idbam)).build();
    }

    /** Pedidos abertos da unidade do usuario, para o medico atender em /base2/resumodealta. */
    @GET
    @Path("/pendentes")
    @br.com.iv.security.UnidadePadraoSessao // sem unidade usa a da sessao
    public Response pendentes(@QueryParam("unidade") String unidadeParam) {
        AuthenticatedUser user = currentUser();
        String unidade = normalizarUnidade(user != null && user.unidade() != null && !user.unidade().isBlank()
                ? user.unidade() : unidadeParam);
        if (unidade.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Unidade nao identificada.")).build();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT s.id, s.idbam, s.solicitado_por, s.solicitado_em, s.motivo_saida, p.idpaciente, p.nome, p.prontuario, "
                        + "EXISTS (SELECT 1 FROM resumodealta r WHERE r.bam = CAST(s.idbam AS text) AND COALESCE(r.status, 'ATIVO') <> 'CANCELADO') "
                        + "FROM resumo_alta_solicitacao s "
                        + "JOIN bam b ON b.idbam = s.idbam "
                        + "LEFT JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente "
                        + "WHERE s.resolvido_em IS NULL AND s.unidade = :unidade "
                        + "AND NOT EXISTS (SELECT 1 FROM assinatura_clinica a WHERE a.idbam = s.idbam "
                        + "  AND a.tipo_documento = 'RESUMO_ALTA' AND upper(a.status) IN ('ASSINADO', 'ADENDO')) "
                        + "ORDER BY s.solicitado_em")
                .setParameter("unidade", unidade)
                .getResultList();
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Object[] l : linhas) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", l[0]);
            item.put("idbam", l[1]);
            item.put("solicitadoPor", l[2]);
            item.put("solicitadoEm", l[3] == null ? null : l[3].toString());
            item.put("motivoSaida", l[4]);
            item.put("idpaciente", l[5]);
            item.put("nome", l[6]);
            item.put("prontuario", l[7]);
            item.put("resumoPreenchido", Boolean.TRUE.equals(l[8]));
            resultado.add(item);
        }
        resultado.addAll(obitosSemResumo(unidade));
        return Response.ok(resultado).build();
    }

    /**
     * BAMs internados da unidade que ja tem Resumo de Alta preenchido (nao cancelado). So os
     * numeros: a tela /resumodealta usa para o filtro "Ocultar preenchidos" sem uma chamada por paciente.
     */
    @GET
    @Path("/bams-com-resumo")
    @br.com.iv.security.UnidadePadraoSessao // sem unidade usa a da sessao
    public Response bamsComResumo(@QueryParam("unidade") String unidadeParam) {
        AuthenticatedUser user = currentUser();
        String unidade = normalizarUnidade(user != null && user.unidade() != null && !user.unidade().isBlank()
                ? user.unidade() : unidadeParam);
        if (unidade.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Unidade nao identificada.")).build();
        }
        List<?> bams = em.createNativeQuery("""
                SELECT DISTINCT n.nbam
                FROM novointernado n
                JOIN bam b ON b.idbam = n.nbam AND b.unidade = :unidade
                WHERE n.alta = false
                  AND EXISTS (SELECT 1 FROM resumodealta r WHERE r.bam = CAST(n.nbam AS text) AND COALESCE(r.status, 'ATIVO') <> 'CANCELADO')
                """)
                .setParameter("unidade", unidade)
                .getResultList();
        return Response.ok(bams).build();
    }

    /**
     * Obito (desfecho da PA, internacao ou saida do leito) sem Resumo de Alta: o paciente
     * que morre na PA nunca entra na lista de internados, entao sem isto o medico nao tinha
     * por onde preencher o resumo. Janela de 90 dias para nao trazer o legado desde 2021.
     */
    static final String SQL_OBITOS_SEM_RESUMO = """
            SELECT b.idbam, b.data, p.idpaciente, p.nome, p.prontuario,
                   COALESCE(
                     (SELECT r.dataobito FROM resumodealta r WHERE r.bam = CAST(b.idbam AS text) AND r.obito = true AND r.dataobito IS NOT NULL AND COALESCE(r.status, 'ATIVO') <> 'CANCELADO' ORDER BY r.idresumoalta DESC LIMIT 1),
                     (SELECT n.datadaacao FROM novointernado n WHERE n.nbam = b.idbam AND n.motivosaida ILIKE '%bito%' AND n.datadaacao IS NOT NULL ORDER BY n.idninternado DESC LIMIT 1),
                     (SELECT i.datasaida FROM internacao i WHERE i.bam = CAST(b.idbam AS text) AND i.motivodaalta ILIKE '%bito%' AND i.datasaida IS NOT NULL ORDER BY i.idninternado DESC LIMIT 1)
                   ) AS dataobito,
                   EXISTS (SELECT 1 FROM resumodealta r WHERE r.bam = CAST(b.idbam AS text) AND COALESCE(r.status, 'ATIVO') <> 'CANCELADO') AS preenchido,
                   EXISTS (SELECT 1 FROM assinatura_clinica a WHERE a.idbam = b.idbam AND a.tipo_documento = 'RESUMO_ALTA' AND upper(a.status) IN ('ASSINADO', 'ADENDO')) AS assinado
            FROM bam b
            JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
            LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
            WHERE b.unidade = :unidade AND b.data >= CURRENT_DATE - 90
              AND (t.tipo_alta ILIKE '%bito%'
                   OR EXISTS (SELECT 1 FROM novointernado n WHERE n.nbam = b.idbam AND n.alta = true AND n.motivosaida ILIKE '%bito%')
                   OR EXISTS (SELECT 1 FROM internacao i WHERE i.bam = CAST(b.idbam AS text) AND i.motivodaalta ILIKE '%bito%'))
              -- Assinado tambem vem (flag "assinado"): a tela so mostra com "Ocultar preenchidos"
              -- desmarcado, para o medico reabrir e registrar adendo.
              AND NOT EXISTS (SELECT 1 FROM resumo_alta_solicitacao s WHERE s.idbam = b.idbam AND s.resolvido_em IS NULL)
            ORDER BY b.data DESC, b.idbam DESC
            LIMIT 200
            """;

    private List<Map<String, Object>> obitosSemResumo(String unidade) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> linhas = em.createNativeQuery(SQL_OBITOS_SEM_RESUMO).setParameter("unidade", unidade).getResultList();
            for (Object[] l : linhas) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", "obito-" + l[0]);
                item.put("idbam", l[0]);
                boolean assinado = Boolean.TRUE.equals(l[7]);
                item.put("resumoAssinado", assinado);
                item.put("motivoSaida", assinado ? "Óbito - resumo assinado"
                        : Boolean.TRUE.equals(l[6]) ? "Óbito - resumo falta assinar" : "Óbito sem resumo de alta");
                item.put("obitoSemResumo", true);
                item.put("idpaciente", l[2]);
                item.put("nome", l[3]);
                item.put("prontuario", l[4]);
                // Data real da saida por obito (NIR/internacao); nunca a abertura do BAM.
                item.put("dataObito", l[5] == null ? null : l[5].toString());
                item.put("dataBam", l[1] == null ? null : l[1].toString());
                item.put("resumoPreenchido", Boolean.TRUE.equals(l[6]) || Boolean.TRUE.equals(l[7]));
                resultado.add(item);
            }
        } catch (RuntimeException e) {
            // Pendencia de obito e complemento: falha aqui nao derruba os pedidos do NIR.
        }
        return resultado;
    }

    /**
     * Medicos ativos da unidade (CBO 225*), para o NIR escolher a quem pedir o resumo.
     * So matricula e nome - nada de entidade Servidor inteira. Quem esta no chat vem primeiro.
     */
    @GET
    @Path("/medicos")
    public Response medicos(@QueryParam("unidade") String unidadeParam) {
        AuthenticatedUser user = currentUser();
        String unidade = normalizarUnidade(user != null && user.unidade() != null && !user.unidade().isBlank()
                ? user.unidade() : unidadeParam);
        if (unidade.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erro", "Unidade nao identificada.")).build();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT matricula, nome FROM servidor WHERE ativo = true AND upper(unidade) LIKE :unidade "
                        + "AND regexp_replace(coalesce(cbo, ''), '\\D', '', 'g') LIKE '225%' ORDER BY nome")
                .setParameter("unidade", unidade.toUpperCase() + "%")
                .getResultList();
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Object[] l : linhas) {
            String matricula = l[0] == null ? "" : String.valueOf(l[0]);
            // Quem pede nao entra na lista: o chat nao mostra mensagem privada para si mesmo.
            if (matricula.isBlank() || (user != null && matricula.equalsIgnoreCase(user.matricula()))) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("matricula", matricula);
            item.put("nome", l[1]);
            // Presença por matrícula: o chat indexa pela unidade da sessão (JWT), não pela do cadastro.
            item.put("online", br.com.iv.ws.ChatPresenceEndpoint.estaConectado(matricula.trim()));
            resultado.add(item);
        }
        resultado.sort((a, b) -> Boolean.compare((Boolean) b.get("online"), (Boolean) a.get("online")));
        return Response.ok(resultado).build();
    }

    private Map<String, Object> montarStatus(int idbam) {
        @SuppressWarnings("unchecked")
        List<Object> aberta = em.createNativeQuery(
                "SELECT solicitado_em FROM resumo_alta_solicitacao WHERE idbam = :id AND resolvido_em IS NULL")
                .setParameter("id", idbam).getResultList();
        Map<String, Object> status = new HashMap<>();
        status.put("idbam", idbam);
        status.put("assinado", resumoAssinado(idbam));
        Number preenchido = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM resumodealta WHERE bam = CAST(:bam AS text) AND COALESCE(status, 'ATIVO') <> 'CANCELADO'")
                .setParameter("bam", idbam).getSingleResult();
        status.put("preenchido", preenchido != null && preenchido.longValue() > 0);
        status.put("exigeAssinatura", exigirResumoAssinado);
        status.put("solicitacaoAberta", !aberta.isEmpty());
        status.put("solicitadoEm", aberta.isEmpty() || aberta.get(0) == null ? null : aberta.get(0).toString());
        status.put("consultadoEm", LocalDateTime.now().toString());
        // Nome do paciente para a mensagem do pedido ao medico (a tela de alta nao o tem em maos).
        List<?> paciente = em.createNativeQuery(
                "SELECT p.nome FROM bam b LEFT JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente WHERE b.idbam = :id")
                .setParameter("id", idbam).getResultList();
        status.put("paciente", paciente.isEmpty() || paciente.get(0) == null ? "" : String.valueOf(paciente.get(0)).trim());
        return status;
    }

    private boolean resumoAssinado(int idbam) {
        Number total = (Number) em.createNativeQuery(
                "SELECT count(*) FROM assinatura_clinica WHERE idbam = :id AND tipo_documento = 'RESUMO_ALTA' "
                        + "AND upper(status) IN ('ASSINADO', 'ADENDO')")
                .setParameter("id", idbam).getSingleResult();
        return total.longValue() > 0;
    }

    private static String normalizarUnidade(String unidade) {
        return unidade == null ? "" : unidade.split(":")[0].trim();
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
        return user != null && !user.matricula().isBlank() ? user : securityPermissionService.resolve(requestContext);
    }
}
