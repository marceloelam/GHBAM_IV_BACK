package br.com.iv.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.ClassificacaoCasoEpidemiologico;
import br.com.iv.service.DefinicaoCasoEpidemiologico;
import br.com.iv.service.RegrasSurtoEpidemiologico;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Registro formal de surto da unidade da sessao: abertura, casos vinculados, medidas e conclusao.
 * Permissao pela entrada "vigilancia-epidemiologica" do RoutePermissionCatalog. Nada aqui altera o prontuario.
 */
@Path("vigilancia-epidemiologica/surtos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SurtoEpidemiologicoResource {
    private static final Logger LOG = Logger.getLogger(SurtoEpidemiologicoResource.class);

    @Inject EntityManager em;
    @Inject SecurityPermissionService securityPermissionService;
    @Context ContainerRequestContext requestContext;

    private record Sessao(String unidade, String matricula, String nome) {}

    /** Sem unidade e profissional identificados nao ha rastreabilidade: a chamada e recusada. */
    private Sessao sessao() {
        String unidade = texto(securityPermissionService.unidadeDaSessao(requestContext)).split(":")[0].trim();
        var usuario = securityPermissionService.resolve(requestContext);
        if (unidade.isBlank() || usuario == null || texto(usuario.matricula()).isBlank()) return null;
        return new Sessao(unidade, usuario.matricula(), texto(usuario.nome()));
    }

    private static Response erro(Response.Status status, String mensagem) {
        return Response.status(status).entity(Map.of("erro", mensagem)).build();
    }

    private static Response semSessao() {
        return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private static String campo(Map<String, Object> dto, String nome) {
        return dto == null ? "" : texto(dto.get(nome));
    }

    private static final String RESUMO_SQL = """
            SELECT s.id, s.regra_id, s.doenca, s.titulo, s.status, s.justificativa, s.profissional, CAST(s.aberto_em AS varchar),
                   CAST(s.concluido_em AS varchar),
                   (SELECT count(DISTINCT sc.idpaciente) FROM vigilancia_surto_caso sc WHERE sc.surto_id = s.id AND sc.removido_em IS NULL) AS pacientes,
                   (SELECT count(DISTINCT sc.idpaciente) FROM vigilancia_surto_caso sc WHERE sc.surto_id = s.id AND sc.removido_em IS NULL
                      AND (SELECT c.classificacao FROM vigilancia_caso_classificacao c
                           WHERE c.regra_id = s.regra_id AND c.idbam = sc.idbam ORDER BY c.id DESC LIMIT 1) = 'CONFIRMADO') AS confirmados
            FROM vigilancia_surto s
            """;

    private static Map<String, Object> resumo(Object[] r) {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] chaves = { "id", "regraId", "doenca", "titulo", "status", "justificativa", "profissional", "abertoEm", "concluidoEm", "pacientes", "pacientesConfirmados" };
        for (int i = 0; i < chaves.length; i++) m.put(chaves[i], r[i]);
        return m;
    }

    @GET
    @Transactional
    public Response listar() {
        Sessao sessao = sessao();
        if (sessao == null) return semSessao();
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(RESUMO_SQL + " WHERE s.unidade = :unidade ORDER BY s.status IN ('DESCARTADO', 'ENCERRADO'), s.id DESC LIMIT 200")
                .setParameter("unidade", sessao.unidade()).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        rows.forEach(r -> out.add(resumo(r)));
        return Response.ok(out).build();
    }

    @POST
    @Transactional
    public Response abrir(Map<String, Object> dto) {
        Sessao sessao = sessao();
        if (sessao == null) return semSessao();
        String titulo = campo(dto, "titulo");
        String justificativa = campo(dto, "justificativa");
        int regraId;
        try {
            regraId = Integer.parseInt(campo(dto, "regraId"));
        } catch (NumberFormatException e) {
            return erro(Response.Status.BAD_REQUEST, "Selecione a regra monitorada.");
        }
        if (titulo.isBlank() || titulo.length() > 160) return erro(Response.Status.BAD_REQUEST, "Informe um título de até 160 caracteres.");
        if (justificativa.isBlank()) return erro(Response.Status.BAD_REQUEST, "Informe a justificativa da abertura.");
        Object[] regra = regra(regraId);
        if (regra == null || !sessao.unidade().equalsIgnoreCase(texto(regra[1]))) return erro(Response.Status.NOT_FOUND, "Regra não encontrada nesta unidade.");
        @SuppressWarnings("unchecked") List<Object> ativos = em.createNativeQuery("SELECT id FROM vigilancia_surto WHERE unidade = :u AND regra_id = :r AND status IN ('EM_INVESTIGACAO', 'CONFIRMADO')")
                .setParameter("u", sessao.unidade()).setParameter("r", regraId).getResultList();
        if (!ativos.isEmpty()) return erro(Response.Status.CONFLICT, "Já existe uma investigação ativa para esta regra.");
        Number id = (Number) em.createNativeQuery("""
                INSERT INTO vigilancia_surto (unidade, regra_id, doenca, titulo, status, justificativa, matricula, profissional)
                VALUES (:u, :r, :d, :t, 'EM_INVESTIGACAO', :j, :m, :p) RETURNING id
                """)
                .setParameter("u", sessao.unidade()).setParameter("r", regraId).setParameter("d", texto(regra[0])).setParameter("t", titulo)
                .setParameter("j", justificativa).setParameter("m", sessao.matricula()).setParameter("p", sessao.nome())
                .getSingleResult();
        evento(id.longValue(), "ABERTURA", justificativa, sessao);
        LOG.infof("Investigacao de surto aberta: id=%d, regra=%d, matricula=%s", id.longValue(), regraId, sessao.matricula());
        return Response.status(Response.Status.CREATED).entity(Map.of("id", id.longValue())).build();
    }

    @GET @Path("{id}")
    @Transactional
    public Response detalhe(@PathParam("id") long id) {
        Sessao sessao = sessao();
        if (sessao == null) return semSessao();
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(RESUMO_SQL + " WHERE s.id = :id AND s.unidade = :unidade")
                .setParameter("id", id).setParameter("unidade", sessao.unidade()).getResultList();
        if (rows.isEmpty()) return erro(Response.Status.NOT_FOUND, "Investigação não encontrada nesta unidade.");
        Map<String, Object> surto = resumo(rows.get(0));
        @SuppressWarnings("unchecked") List<Object[]> casos = em.createNativeQuery("""
                SELECT sc.idbam, p.nome, CAST(b.data AS varchar), sc.profissional,
                       (SELECT c.classificacao FROM vigilancia_caso_classificacao c
                        WHERE c.regra_id = :regra AND c.idbam = sc.idbam ORDER BY c.id DESC LIMIT 1)
                FROM vigilancia_surto_caso sc
                JOIN bam b ON b.idbam = sc.idbam
                LEFT JOIN pacientes p ON p.idpaciente = sc.idpaciente
                WHERE sc.surto_id = :id AND sc.removido_em IS NULL
                ORDER BY b.data DESC, sc.idbam DESC
                """).setParameter("id", id).setParameter("regra", surto.get("regraId")).getResultList();
        List<Map<String, Object>> listaCasos = new ArrayList<>();
        for (Object[] c : casos) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bam", c[0]); m.put("paciente", c[1]); m.put("data", c[2]); m.put("vinculadoPor", c[3]);
            m.put("classificacao", c[4] == null ? ClassificacaoCasoEpidemiologico.SUSPEITO : c[4]);
            listaCasos.add(m);
        }
        @SuppressWarnings("unchecked") List<Object[]> eventos = em.createNativeQuery(
                "SELECT tipo, descricao, profissional, CAST(criado_em AS varchar) FROM vigilancia_surto_evento WHERE surto_id = :id ORDER BY id DESC")
                .setParameter("id", id).getResultList();
        List<Map<String, Object>> listaEventos = new ArrayList<>();
        for (Object[] e : eventos) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", e[0]); m.put("descricao", e[1]); m.put("profissional", e[2]); m.put("em", e[3]);
            listaEventos.add(m);
        }
        surto.put("casos", listaCasos);
        surto.put("eventos", listaEventos);
        // Rastreabilidade do acesso a dado identificado; o log nao leva dado do paciente.
        LOG.infof("Investigacao de surto consultada: id=%d, matricula=%s, casos=%d", id, sessao.matricula(), listaCasos.size());
        return Response.ok(surto).build();
    }

    @POST @Path("{id}/casos")
    @Transactional
    public Response vincular(@PathParam("id") long id, Map<String, Object> dto) {
        Sessao sessao = sessao();
        if (sessao == null) return semSessao();
        Object[] surto = surto(id, sessao);
        if (surto == null) return erro(Response.Status.NOT_FOUND, "Investigação não encontrada nesta unidade.");
        int bam;
        try {
            bam = Integer.parseInt(campo(dto, "bam"));
        } catch (NumberFormatException e) {
            return erro(Response.Status.BAD_REQUEST, "Informe o BAM.");
        }
        @SuppressWarnings("unchecked") List<Object[]> atendimento = em.createNativeQuery("SELECT unidade, paciente_idpaciente FROM bam WHERE idbam = :bam")
                .setParameter("bam", bam).getResultList();
        if (atendimento.isEmpty()) return erro(Response.Status.NOT_FOUND, "BAM não encontrado.");
        if (!sessao.unidade().equalsIgnoreCase(texto(atendimento.get(0)[0]))) return erro(Response.Status.FORBIDDEN, "Este BAM não pertence à unidade da sessão.");
        @SuppressWarnings("unchecked") List<Object> classificacao = em.createNativeQuery(
                "SELECT classificacao FROM vigilancia_caso_classificacao WHERE regra_id = :r AND idbam = :b ORDER BY id DESC LIMIT 1")
                .setParameter("r", surto[0]).setParameter("b", bam).getResultList();
        String invalido = RegrasSurtoEpidemiologico.validarVinculo(classificacao.isEmpty() ? null : texto(classificacao.get(0)), texto(surto[1]));
        if (invalido != null) return erro(Response.Status.CONFLICT, invalido);
        @SuppressWarnings("unchecked") List<Object> existente = em.createNativeQuery("SELECT id FROM vigilancia_surto_caso WHERE surto_id = :s AND idbam = :b AND removido_em IS NULL")
                .setParameter("s", id).setParameter("b", bam).getResultList();
        if (!existente.isEmpty()) return erro(Response.Status.CONFLICT, "Este BAM já está vinculado.");
        em.createNativeQuery("INSERT INTO vigilancia_surto_caso (surto_id, idbam, idpaciente, matricula, profissional) VALUES (:s, :b, :p, :m, :n)")
                .setParameter("s", id).setParameter("b", bam).setParameter("p", atendimento.get(0)[1])
                .setParameter("m", sessao.matricula()).setParameter("n", sessao.nome()).executeUpdate();
        evento(id, "CASO_VINCULADO", "BAM " + bam, sessao);
        return Response.status(Response.Status.CREATED).build();
    }

    /** O vinculo nao e apagado: fica marcado como removido, com a justificativa no historico. */
    @POST @Path("{id}/casos/{bam}/remover")
    @Transactional
    public Response removerCaso(@PathParam("id") long id, @PathParam("bam") int bam, Map<String, Object> dto) {
        Sessao sessao = sessao();
        if (sessao == null) return semSessao();
        Object[] surto = surto(id, sessao);
        if (surto == null) return erro(Response.Status.NOT_FOUND, "Investigação não encontrada nesta unidade.");
        String justificativa = campo(dto, "justificativa");
        if (justificativa.isBlank()) return erro(Response.Status.BAD_REQUEST, "Informe a justificativa.");
        // A declaracao precisa continuar valendo: o surto confirmado nao pode perder o caso que a sustenta.
        if (!RegrasSurtoEpidemiologico.EM_INVESTIGACAO.equals(texto(surto[1]))) return erro(Response.Status.CONFLICT, "Só é possível remover casos durante a investigação.");
        int total = em.createNativeQuery("UPDATE vigilancia_surto_caso SET removido_em = now() WHERE surto_id = :s AND idbam = :b AND removido_em IS NULL")
                .setParameter("s", id).setParameter("b", bam).executeUpdate();
        if (total == 0) return erro(Response.Status.NOT_FOUND, "Este BAM não está vinculado.");
        evento(id, "CASO_REMOVIDO", "BAM " + bam + ". " + justificativa, sessao);
        return Response.noContent().build();
    }

    @POST @Path("{id}/medidas")
    @Transactional
    public Response registrarMedida(@PathParam("id") long id, Map<String, Object> dto) {
        Sessao sessao = sessao();
        if (sessao == null) return semSessao();
        Object[] surto = surto(id, sessao);
        if (surto == null) return erro(Response.Status.NOT_FOUND, "Investigação não encontrada nesta unidade.");
        String medida = campo(dto, "descricao");
        if (medida.isBlank()) return erro(Response.Status.BAD_REQUEST, "Descreva a medida adotada.");
        evento(id, "MEDIDA", medida, sessao);
        return Response.status(Response.Status.CREATED).build();
    }

    @POST @Path("{id}/situacao")
    @Transactional
    public Response mudarSituacao(@PathParam("id") long id, Map<String, Object> dto) {
        Sessao sessao = sessao();
        if (sessao == null) return semSessao();
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(RESUMO_SQL + " WHERE s.id = :id AND s.unidade = :unidade")
                .setParameter("id", id).setParameter("unidade", sessao.unidade()).getResultList();
        if (rows.isEmpty()) return erro(Response.Status.NOT_FOUND, "Investigação não encontrada nesta unidade.");
        Map<String, Object> atual = resumo(rows.get(0));
        Object[] regra = regra(((Number) atual.get("regraId")).intValue());
        boolean casoUnico = regra != null && DefinicaoCasoEpidemiologico.casoUnicoImporta(texto(regra[0]), texto(regra[2]));
        String novo = campo(dto, "status").toUpperCase(java.util.Locale.ROOT);
        String justificativa = campo(dto, "justificativa");
        String invalido = RegrasSurtoEpidemiologico.validarMudanca(texto(atual.get("status")), novo, justificativa,
                ((Number) atual.get("pacientesConfirmados")).longValue(), casoUnico);
        if (invalido != null) return erro(Response.Status.CONFLICT, invalido);
        // A condicao no status evita duas conclusoes simultaneas sobre a mesma investigacao.
        int total = em.createNativeQuery("""
                UPDATE vigilancia_surto SET status = :novo,
                    concluido_em = CASE WHEN :novo IN ('DESCARTADO', 'ENCERRADO') THEN now() ELSE concluido_em END
                WHERE id = :id AND status = :atual
                """).setParameter("novo", novo).setParameter("id", id).setParameter("atual", texto(atual.get("status"))).executeUpdate();
        if (total == 0) return erro(Response.Status.CONFLICT, "A situação foi alterada por outra pessoa. Atualize a tela.");
        evento(id, novo, justificativa, sessao);
        LOG.infof("Situacao de surto alterada: id=%d, status=%s, matricula=%s", id, novo, sessao.matricula());
        return Response.noContent().build();
    }

    /** {nome, unidade, termos} ou null. */
    private Object[] regra(int regraId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("SELECT nome, unidade, termos FROM vigilancia_epidemiologica_regra WHERE id = :id")
                .setParameter("id", regraId).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {regra_id, status} do surto na unidade da sessao, ou null. */
    private Object[] surto(long id, Sessao sessao) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("SELECT regra_id, status FROM vigilancia_surto WHERE id = :id AND unidade = :u")
                .setParameter("id", id).setParameter("u", sessao.unidade()).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void evento(long surtoId, String tipo, String descricao, Sessao sessao) {
        em.createNativeQuery("INSERT INTO vigilancia_surto_evento (surto_id, tipo, descricao, matricula, profissional) VALUES (:s, :t, :d, :m, :p)")
                .setParameter("s", surtoId).setParameter("t", tipo).setParameter("d", descricao)
                .setParameter("m", sessao.matricula()).setParameter("p", sessao.nome()).executeUpdate();
    }
}
