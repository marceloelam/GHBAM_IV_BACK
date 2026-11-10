package br.com.iv.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;
import br.com.iv.repository.Servidor2Repository;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.dto.LogDto;
import br.com.iv.security.AuditPacienteResolver;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.LoginRateLimitService;
import br.com.iv.service.LogService;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.RegrasCoordenacaoSetor;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Coordenacao de setor na unidade da sessao. A administracao define coordenadores e equipes
 * (coordenacao-setor/gestao, so kadm); o coordenador altera acessos da propria equipe, um por vez,
 * com trilha em setor_permissao_auditoria. RBAC: RoutePermissionCatalog "coordenacao-setor".
 */
@Path("coordenacao-setor")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CoordenacaoSetorResource {
    private static final Logger LOG = Logger.getLogger(CoordenacaoSetorResource.class);

    @Inject EntityManager em;
    @Inject SecurityPermissionService securityPermissionService;
    @Inject Servidor2Repository servidor2Repository;
    @Inject ServidorRepository servidorRepository;
    @Inject LoginRateLimitService loginRateLimitService;
    @Inject LogService logService;
    @Context ContainerRequestContext requestContext;

    private static final java.time.format.DateTimeFormatter HORA_LOG = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Registro no log do sistema (tela Logs do Sistema): quem fez, unidade e todos os envolvidos.
     * Nunca grava senha. bam (opcional) vincula o registro ao paciente.
     */
    private void logSistema(Sessao s, String acao, String bam) {
        try {
            LogDto dto = new LogDto();
            dto.setData(java.time.LocalDate.now());
            dto.setHora(java.time.LocalTime.now().format(HORA_LOG));
            // "| usuario=... | matricula=..." no fim: formato que a tela Logs do Sistema usa para agrupar e mostrar a foto.
            dto.setEvento("COORDENAÇÃO DE SETOR | " + acao + " | por " + (s.nome().isBlank() ? "-" : s.nome())
                    + " (matrícula " + s.matricula() + ") | unidade " + s.unidade()
                    + " | usuario=" + (s.nome().isBlank() ? "Usuario autenticado" : s.nome()) + " | matricula=" + s.matricula());
            dto.setUnidade(s.unidade().length() > 20 ? s.unidade().substring(0, 20) : s.unidade());
            if (bam != null && bam.matches("\\d{1,9}")) {
                dto.setPacienteTipo(AuditPacienteResolver.TIPO_BAM);
                dto.setPacienteValor(bam);
            }
            logService.salvarLogAsync(dto);
        } catch (Exception e) {
            LOG.warnf("Falha ao registrar log da coordenacao de setor: %s", e.getMessage());
        }
    }

    /** "NOME (matrícula X)" para o log; sem cadastro, só a matrícula. */
    private String pessoa(String matricula) {
        Servidor2 s2 = matricula == null || matricula.isBlank() ? null : servidor2Repository.localizarMatricula(matricula);
        String nome = s2 == null ? "" : texto(s2.getNome());
        return (nome.isBlank() ? "matrícula " + matricula : nome + " (matrícula " + matricula + ")");
    }

    private record Sessao(String unidade, String matricula, String nome, boolean administrador) {}

    private record Vinculo(long id, String setor, String matricula, String papel) {}

    private Sessao sessao() {
        AuthenticatedUser usuario = securityPermissionService.resolve(requestContext);
        if (usuario == null || texto(usuario.matricula()).isBlank()) return null;
        String unidade = texto(securityPermissionService.unidadeDaSessao(requestContext));
        if (unidade.isBlank()) unidade = texto(usuario.unidade());
        unidade = unidade.split(":")[0].trim();
        if (unidade.isBlank()) return null;
        return new Sessao(unidade, usuario.matricula(), texto(usuario.nome()), usuario.hasPermission("kadm"));
    }

    private static Response erro(Response.Status status, String mensagem) {
        return Response.status(status).entity(Map.of("erro", mensagem)).build();
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    @SuppressWarnings("unchecked")
    private List<Vinculo> vinculos(String unidade) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, setor, matricula, papel FROM setor_coordenacao
                WHERE unidade = :u AND removido_em IS NULL ORDER BY setor, papel, matricula
                """).setParameter("u", unidade).getResultList();
        List<Vinculo> out = new ArrayList<>();
        for (Object[] r : rows) {
            Vinculo v = new Vinculo(((Number) r[0]).longValue(), texto(r[1]), texto(r[2]), texto(r[3]));
            // A coordenacao e da unidade: conta desativada/bloqueada ou servidor sem vinculo com a unidade
            // (transferido) perde o papel automaticamente, com registro no banco e no log.
            String motivo = motivoPerdaVinculo(v.matricula(), unidade, v.papel());
            if (motivo != null) {
                retirarAutomaticamente(v, unidade, motivo);
                continue;
            }
            out.add(v);
        }
        return out;
    }

    /**
     * Coordenador exige conta ativa e liberada (perde a coordenação se for desativado/bloqueado). Membro só precisa
     * do vínculo com a unidade: estar na equipe não dá poder, e quem está bloqueado apenas não entra no sistema.
     */
    private String motivoPerdaVinculo(String matricula, String unidade, String papel) {
        Servidor sv = servidorRepository.localizarMatricula(matricula);
        if (sv == null) return "servidor não encontrado no cadastro";
        if (RegrasCoordenacaoSetor.COORDENADOR.equals(papel) && !securityPermissionService.acessoPermitido(sv)) {
            return "conta desativada ou bloqueada";
        }
        if (!securityPermissionService.temAcessoAUnidade(sv, unidade)) {
            return "sem vínculo com a unidade " + unidade + " (transferido ou desvinculado)";
        }
        return null;
    }

    private void retirarAutomaticamente(Vinculo v, String unidade, String motivo) {
        int alterados = em.createNativeQuery(
                "UPDATE setor_coordenacao SET removido_em = now(), removido_por = 'SISTEMA' WHERE id = :id AND removido_em IS NULL")
                .setParameter("id", v.id()).executeUpdate();
        if (alterados == 0) return;
        securityPermissionService.invalidateUserCache(v.matricula());
        LOG.infof("Coordenacao de setor: retirada automatica. matricula=%s setor=%s unidade=%s motivo=%s", v.matricula(), v.setor(), unidade, motivo);
        logSistema(new Sessao(unidade, "SISTEMA", "Revisão automática", false), "RETIRADA AUTOMÁTICA de " + pessoa(v.matricula())
                + " do setor " + v.setor() + " (era " + (RegrasCoordenacaoSetor.COORDENADOR.equals(v.papel()) ? "COORDENADOR" : "EQUIPE")
                + ") | motivo: " + motivo, null);
    }

    /** O coordenador retira alguém da equipe do próprio setor (não retira coordenadores nem a si mesmo). */
    @POST
    @Path("equipes/remover")
    @Transactional
    public Response removerDaEquipe(Map<String, Object> dto) {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        String setor = texto(dto == null ? null : dto.get("setor"));
        String matricula = texto(dto == null ? null : dto.get("matricula"));
        if (setor.isBlank() || matricula.isBlank()) return erro(Response.Status.BAD_REQUEST, "Informe o setor e o servidor.");
        String justificativa = texto(dto.get("justificativa"));
        if (justificativa.length() < 10) {
            return erro(Response.Status.BAD_REQUEST, "Informe a justificativa da retirada (ao menos 10 caracteres).");
        }
        if (justificativa.length() > 500) justificativa = justificativa.substring(0, 500);
        List<Vinculo> vinculos = vinculos(s.unidade());
        boolean coordena = setoresCoordenados(vinculos, s).contains(setor);
        Vinculo alvo = vinculos.stream().filter(v -> v.setor().equals(setor) && v.matricula().equalsIgnoreCase(matricula)).findFirst().orElse(null);
        String recusa = null;
        if (!s.administrador() && !coordena) recusa = "Você não coordena este setor.";
        else if (alvo == null) recusa = "O servidor não faz parte da equipe deste setor.";
        else if (matricula.equalsIgnoreCase(s.matricula())) recusa = "Ninguém se retira da equipe por aqui. Peça à administração.";
        else if (!s.administrador() && !RegrasCoordenacaoSetor.MEMBRO.equals(alvo.papel())) recusa = "Coordenador não retira outro coordenador. Peça à administração.";
        if (recusa != null) {
            logSistema(s, "TENTATIVA RECUSADA de retirar " + pessoa(matricula) + " da equipe do setor " + setor + " | motivo: " + recusa, null);
            return erro(Response.Status.FORBIDDEN, recusa);
        }
        em.createNativeQuery("UPDATE setor_coordenacao SET removido_em = now(), removido_por = :autor WHERE id = :id AND removido_em IS NULL")
                .setParameter("autor", s.matricula()).setParameter("id", alvo.id()).executeUpdate();
        securityPermissionService.invalidateUserCache(matricula);
        logSistema(s, "RETIROU " + pessoa(matricula) + " da equipe do setor " + setor + (s.administrador() ? "" : " (pela coordenação)")
                + " | justificativa: " + justificativa + " | os acessos atuais da pessoa não foram alterados", null);
        return Response.ok(Map.of("setor", setor, "matricula", matricula, "removido", true)).build();
    }

    /**
     * Setores (grupos do menu) da unidade da sessao que tem coordenador e cuja equipe nao inclui a sessao: o menu
     * e as telas desses grupos ficam fechados para ela, mesmo com o acesso ligado em /servidores. Setor sem
     * coordenador segue so pelas permissoes. Administracao e superusuario nao sofrem a restricao.
     */
    @GET
    @Path("meus-setores")
    @Transactional
    public Response meusSetores() {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        AuthenticatedUser usuario = securityPermissionService.resolve(requestContext);
        if (s.administrador() || usuario.hasPermission("ksuperusuario") || usuario.hasPermission("superusuario")) {
            return Response.ok(Map.of("unidade", s.unidade(), "restritos", List.of(), "equipes", List.of())).build();
        }
        List<Vinculo> vinculos = vinculos(s.unidade());
        Set<String> comCoordenador = new LinkedHashSet<>();
        Set<String> minhas = new LinkedHashSet<>();
        for (Vinculo v : vinculos) {
            if (RegrasCoordenacaoSetor.COORDENADOR.equals(v.papel())) comCoordenador.add(v.setor());
            if (v.matricula().equalsIgnoreCase(s.matricula())) minhas.add(v.setor());
        }
        List<String> restritos = comCoordenador.stream().filter(setor -> !minhas.contains(setor)).sorted().toList();
        return Response.ok(Map.of("unidade", s.unidade(), "restritos", restritos, "equipes", List.copyOf(minhas))).build();
    }

    private static Set<String> setoresCoordenados(List<Vinculo> vinculos, Sessao s) {
        Set<String> out = new LinkedHashSet<>();
        for (Vinculo v : vinculos) {
            if (s.administrador() || (v.matricula().equalsIgnoreCase(s.matricula()) && RegrasCoordenacaoSetor.COORDENADOR.equals(v.papel()))) {
                out.add(v.setor());
            }
        }
        return out;
    }

    private List<String> concediveis(Sessao s) {
        Set<String> doAutor = s.administrador() ? Set.of() : RegrasCoordenacaoSetor.ligados(servidor2Repository.localizarMatricula(s.matricula()));
        return RegrasCoordenacaoSetor.concediveis(RegrasCoordenacaoSetor.chavesServidor2(), doAutor, s.administrador());
    }

    /** Equipes que a sessao coordena (administracao: todas da unidade), com os acessos concediveis de cada membro. */
    @GET
    @Path("equipes")
    @Transactional
    public Response equipes() {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        List<Vinculo> vinculos = vinculos(s.unidade());
        Set<String> setores = setoresCoordenados(vinculos, s);
        if (!s.administrador() && setores.isEmpty()) return erro(Response.Status.FORBIDDEN, "Você não coordena nenhum setor nesta unidade.");
        List<String> chaves = concediveis(s);
        Servidor autor = s.administrador() ? null : servidorRepository.localizarMatricula(s.matricula());

        List<Map<String, Object>> lista = new ArrayList<>();
        for (String setor : setores) {
            // Cada setor (grupo do menu) concede so os acessos e perfis que abrem aquele grupo.
            List<String> chavesSetor = RegrasCoordenacaoSetor.acessosDoSetor(setor, chaves);
            List<String> perfisSetor = RegrasCoordenacaoSetor.perfisDoSetor(setor).stream()
                    .filter(p -> s.administrador() || Boolean.TRUE.equals(RegrasCoordenacaoSetor.valorPerfil(autor, p)))
                    .toList();
            List<Map<String, Object>> coordenadores = new ArrayList<>();
            List<Map<String, Object>> membros = new ArrayList<>();
            for (Vinculo v : vinculos) {
                if (!v.setor().equals(setor)) continue;
                Servidor2 s2 = servidor2Repository.localizarMatricula(v.matricula());
                Map<String, Object> pessoa = new LinkedHashMap<>();
                pessoa.put("matricula", v.matricula());
                pessoa.put("nome", s2 == null ? "(sem cadastro de acessos)" : texto(s2.getNome()));
                pessoa.put("papel", v.papel());
                boolean editavel = s2 != null && !v.matricula().equalsIgnoreCase(s.matricula())
                        && (s.administrador() || RegrasCoordenacaoSetor.MEMBRO.equals(v.papel()));
                pessoa.put("editavel", editavel);
                if (editavel) {
                    Map<String, Boolean> valores = new LinkedHashMap<>();
                    Map<String, String> bloqueios = new LinkedHashMap<>();
                    Servidor alvo = perfisSetor.isEmpty() ? null : servidorRepository.localizarMatricula(v.matricula());
                    for (String perfil : perfisSetor) {
                        String chave = RegrasCoordenacaoSetor.PREFIXO_PERFIL + perfil;
                        boolean ligado = Boolean.TRUE.equals(RegrasCoordenacaoSetor.valorPerfil(alvo, perfil));
                        valores.put(chave, ligado);
                        String motivo = alvo == null ? "Servidor não encontrado." : RegrasCoordenacaoSetor.recusaPerfil(perfil, alvo.getCargo());
                        if (motivo != null && !ligado) bloqueios.put(chave, motivo);
                    }
                    for (String chave : chavesSetor) valores.put(chave, Boolean.TRUE.equals(RegrasCoordenacaoSetor.valor(s2, chave)));
                    pessoa.put("valores", valores);
                    pessoa.put("bloqueios", bloqueios);
                }
                (RegrasCoordenacaoSetor.COORDENADOR.equals(v.papel()) ? coordenadores : membros).add(pessoa);
            }
            List<String> chavesItem = new ArrayList<>();
            perfisSetor.forEach(p -> chavesItem.add(RegrasCoordenacaoSetor.PREFIXO_PERFIL + p));
            chavesItem.addAll(chavesSetor);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("setor", setor);
            item.put("chaves", chavesItem);
            item.put("coordenadores", coordenadores);
            item.put("membros", membros);
            lista.add(item);
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("unidade", s.unidade());
        resposta.put("administrador", s.administrador());
        resposta.put("chaves", chaves);
        resposta.put("setores", lista);
        return Response.ok(resposta).build();
    }

    /** Liga ou desliga um acesso de um membro da equipe. Corpo: setor, matricula, chave, valor, justificativa. */
    @PUT
    @Path("equipes/permissao")
    @Transactional
    public Response alterarPermissao(Map<String, Object> dto) {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        String setor = texto(dto == null ? null : dto.get("setor"));
        String matricula = texto(dto == null ? null : dto.get("matricula"));
        String chave = texto(dto == null ? null : dto.get("chave"));
        Object bruto = dto == null ? null : dto.get("valor");
        if (setor.isBlank() || matricula.isBlank() || chave.isBlank() || !(bruto instanceof Boolean)) {
            return erro(Response.Status.BAD_REQUEST, "Informe setor, servidor, acesso e o novo valor.");
        }
        boolean valor = (Boolean) bruto;
        String justificativa = texto(dto.get("justificativa"));
        if (justificativa.length() > 500) justificativa = justificativa.substring(0, 500);

        List<Vinculo> vinculos = vinculos(s.unidade());
        boolean coordena = setoresCoordenados(vinculos, s).contains(setor);
        String papelAlvo = vinculos.stream()
                .filter(v -> v.setor().equals(setor) && v.matricula().equalsIgnoreCase(matricula))
                .map(Vinculo::papel).findFirst().orElse(null);
        if (chave.startsWith(RegrasCoordenacaoSetor.PREFIXO_PERFIL)) {
            return alterarPerfil(s, setor, matricula, chave.substring(RegrasCoordenacaoSetor.PREFIXO_PERFIL.length()),
                    valor, justificativa, coordena, papelAlvo);
        }
        List<String> chaves = concediveis(s);
        String recusa = RegrasCoordenacaoSetor.recusa(s.administrador(), coordena, papelAlvo, s.matricula(), matricula, chave, chaves);
        if (recusa == null && RegrasCoordenacaoSetor.acessosDoSetor(setor, List.of(chave)).isEmpty()) {
            recusa = "Este acesso não pertence ao menu deste setor.";
        }
        if (recusa != null) {
            LOG.warnf("Coordenacao de setor recusou alteracao. autor=%s alvo=%s setor=%s chave=%s motivo=%s",
                    s.matricula(), matricula, setor, chave, recusa);
            logSistema(s, "TENTATIVA RECUSADA de " + (valor ? "liberar" : "retirar") + " o acesso '" + chave + "' de "
                    + pessoa(matricula) + " no setor " + setor + " | motivo: " + recusa, null);
            return erro(Response.Status.FORBIDDEN, recusa);
        }
        Servidor2 alvo = servidor2Repository.localizarMatricula(matricula);
        if (alvo == null) return erro(Response.Status.NOT_FOUND, "O servidor não tem cadastro de acessos.");
        Boolean anterior = RegrasCoordenacaoSetor.valor(alvo, chave);
        if (Boolean.valueOf(valor).equals(anterior)) {
            return Response.ok(Map.of("matricula", matricula, "chave", chave, "valor", valor, "alterado", false)).build();
        }
        // A coluna vem da lista de campos da entidade (validada em recusa), nunca do texto do navegador.
        em.createNativeQuery("UPDATE servidor2 SET " + chave + " = :v WHERE idservidor2 = :id")
                .setParameter("v", valor).setParameter("id", alvo.getIdservidor2()).executeUpdate();
        auditar(s, setor, matricula, chave, anterior, valor, justificativa);
        return Response.ok(Map.of("matricula", matricula, "chave", chave, "valor", valor, "alterado", true)).build();
    }

    /** Perfil do grupo do menu (tabela servidor), com a regra do cargo e sem conceder acima do proprio perfil. */
    private Response alterarPerfil(Sessao s, String setor, String matricula, String perfil, boolean valor, String justificativa,
                                   boolean coordena, String papelAlvo) {
        String chave = RegrasCoordenacaoSetor.PREFIXO_PERFIL + perfil;
        String recusa = RegrasCoordenacaoSetor.recusaVinculo(s.administrador(), coordena, papelAlvo, s.matricula(), matricula);
        if (recusa == null && !RegrasCoordenacaoSetor.perfisDoSetor(setor).contains(perfil)) {
            recusa = "Este perfil não pertence ao menu deste setor.";
        }
        Servidor alvo = recusa == null ? servidorRepository.localizarMatricula(matricula) : null;
        if (recusa == null && alvo == null) recusa = "Servidor não encontrado.";
        // Desligar sempre pode (inclusive perfil antigo fora da regra do cargo); ligar respeita o cargo.
        if (recusa == null && valor) recusa = RegrasCoordenacaoSetor.recusaPerfil(perfil, alvo.getCargo());
        if (recusa == null && !s.administrador()
                && !Boolean.TRUE.equals(RegrasCoordenacaoSetor.valorPerfil(servidorRepository.localizarMatricula(s.matricula()), perfil))) {
            recusa = "Você só pode conceder perfis que você mesmo possui.";
        }
        if (recusa != null) {
            LOG.warnf("Coordenacao de setor recusou perfil. autor=%s alvo=%s setor=%s perfil=%s motivo=%s",
                    s.matricula(), matricula, setor, perfil, recusa);
            logSistema(s, "TENTATIVA RECUSADA de " + (valor ? "ligar" : "desligar") + " o perfil '" + perfil + "' de "
                    + pessoa(matricula) + " no setor " + setor + " | motivo: " + recusa, null);
            return erro(Response.Status.FORBIDDEN, recusa);
        }
        Boolean anterior = RegrasCoordenacaoSetor.valorPerfil(alvo, perfil);
        if (Boolean.valueOf(valor).equals(anterior)) {
            return Response.ok(Map.of("matricula", matricula, "chave", chave, "valor", valor, "alterado", false)).build();
        }
        // A coluna vem de PERFIS_CONCEDIVEIS (validada acima), nunca do texto do navegador.
        em.createNativeQuery("UPDATE servidor SET " + perfil + " = :v WHERE idservidor = :id")
                .setParameter("v", valor).setParameter("id", alvo.getIdservidor()).executeUpdate();
        auditar(s, setor, matricula, chave, anterior, valor, justificativa);
        return Response.ok(Map.of("matricula", matricula, "chave", chave, "valor", valor, "alterado", true)).build();
    }

    private void auditar(Sessao s, String setor, String matricula, String chave, Boolean anterior, boolean valor, String justificativa) {
        em.createNativeQuery("""
                INSERT INTO setor_permissao_auditoria
                    (unidade, setor, matricula_autor, nome_autor, matricula_alvo, chave, valor_anterior, valor_novo, justificativa)
                VALUES (:u, :s, :autor, :nome, :alvo, :chave, :anterior, :novo, :just)
                """)
                .setParameter("u", s.unidade()).setParameter("s", setor).setParameter("autor", s.matricula())
                .setParameter("nome", s.nome().length() > 80 ? s.nome().substring(0, 80) : s.nome())
                .setParameter("alvo", matricula).setParameter("chave", chave)
                .setParameter("anterior", anterior).setParameter("novo", valor)
                .setParameter("just", justificativa == null || justificativa.isBlank() ? null : justificativa)
                .executeUpdate();
        securityPermissionService.invalidateUserCache(matricula);
        LOG.infof("Coordenacao de setor alterou acesso. autor=%s alvo=%s setor=%s chave=%s %s->%s",
                s.matricula(), matricula, setor, chave, anterior, valor);
        boolean perfil = chave.startsWith(RegrasCoordenacaoSetor.PREFIXO_PERFIL);
        String item = perfil ? "o perfil '" + chave.substring(RegrasCoordenacaoSetor.PREFIXO_PERFIL.length()) + "'" : "o acesso '" + chave + "'";
        logSistema(s, (valor ? "LIBEROU " : "RETIROU ") + item + (valor ? " para " : " de ") + pessoa(matricula)
                + " no setor " + setor + " | antes: " + (Boolean.TRUE.equals(anterior) ? "ligado" : "desligado")
                + ", agora: " + (valor ? "ligado" : "desligado")
                + (justificativa == null || justificativa.isBlank() ? "" : " | justificativa: " + justificativa), null);
    }

    /** Ultimas alteracoes nos setores que a sessao coordena (administracao: todos da unidade). */
    @GET
    @Path("auditoria")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response auditoria(@QueryParam("setor") String setor) {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        Set<String> setores = setoresCoordenados(vinculos(s.unidade()), s);
        if (!texto(setor).isBlank()) {
            if (!setores.contains(texto(setor))) return erro(Response.Status.FORBIDDEN, "Você não coordena este setor.");
            setores = Set.of(texto(setor));
        }
        if (setores.isEmpty()) return Response.ok(List.of()).build();
        List<Object[]> rows = em.createNativeQuery("""
                SELECT setor, matricula_autor, nome_autor, matricula_alvo, chave, valor_anterior, valor_novo, justificativa,
                       CAST(alterado_em AS varchar)
                FROM setor_permissao_auditoria
                WHERE unidade = :u AND setor = ANY(CAST(string_to_array(:setores, '|') AS varchar[]))
                ORDER BY alterado_em DESC LIMIT 200
                """).setParameter("u", s.unidade()).setParameter("setores", String.join("|", setores)).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("setor", r[0]); m.put("autor", r[1]); m.put("nomeAutor", r[2]); m.put("alvo", r[3]);
            m.put("chave", r[4]); m.put("anterior", r[5]); m.put("novo", r[6]); m.put("justificativa", r[7]); m.put("em", r[8]);
            out.add(m);
        }
        return Response.ok(out).build();
    }

    /**
     * Ultima alteracao feita pela coordenacao em cada acesso/perfil de um servidor, na unidade da sessao.
     * So administracao: alimenta a anotacao "ligado pela coordenacao do X" na tela de servidores.
     * Chave do mapa: coluna de servidor2 ou "perfil:<coluna de servidor>".
     */
    @GET
    @Path("origem/{matricula}")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response origem(@PathParam("matricula") String matricula) {
        Sessao s = sessao();
        Response negado = exigirAdministracao(s);
        if (negado != null) return negado;
        List<Object[]> rows = em.createNativeQuery("""
                SELECT DISTINCT ON (chave) chave, setor, matricula_autor, nome_autor, valor_novo, CAST(alterado_em AS varchar)
                FROM setor_permissao_auditoria
                WHERE unidade = :u AND matricula_alvo = :m
                ORDER BY chave, alterado_em DESC
                """).setParameter("u", s.unidade()).setParameter("m", texto(matricula)).getResultList();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("setor", r[1]); m.put("autor", r[2]); m.put("nomeAutor", r[3]); m.put("valor", r[4]); m.put("em", r[5]);
            out.put(String.valueOf(r[0]), m);
        }
        return Response.ok(out).build();
    }

    // ---- autorizacao por segunda pessoa (ex.: cancelar alta no NIR) -----------------------------

    /**
     * Confere se outra pessoa (matricula + senha) coordena o setor na unidade da sessao. Usado onde uma
     * operacao sensivel exige a liberacao da coordenacao. Administracao tambem autoriza (contingencia).
     * Corpo: matricula, senha, setor (padrao "Nir").
     */
    @POST
    @Path("autorizar-coordenador")
    @Transactional
    public Response autorizarCoordenador(Map<String, Object> dto) {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        String matricula = texto(dto == null ? null : dto.get("matricula"));
        String senha = dto == null || dto.get("senha") == null ? "" : String.valueOf(dto.get("senha"));
        String setor = texto(dto == null ? null : dto.get("setor"));
        if (setor.isBlank()) setor = "Nir";
        if (matricula.isBlank() || senha.isBlank()) return erro(Response.Status.BAD_REQUEST, "Informe matrícula e senha do coordenador.");
        String tipoCoord = "liberação da coordenação do " + setor;
        String bamCoord = texto(dto.get("bam"));
        if (matricula.equalsIgnoreCase(s.matricula())) {
            return recusarLiberacao(s, tipoCoord, matricula, bamCoord, Response.Status.FORBIDDEN,
                    "A liberação da coordenação precisa ser de outra pessoa, não do usuário logado.");
        }
        if (loginRateLimitService.segundosBloqueadoMatricula(matricula) > 0) {
            return recusarLiberacao(s, tipoCoord, matricula, bamCoord, Response.Status.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.");
        }
        AuthenticatedUser autorizador = securityPermissionService.authenticate(matricula, senha);
        if (autorizador == null) {
            loginRateLimitService.registrarFalhaMatricula(matricula);
            return recusarLiberacao(s, tipoCoord, matricula, bamCoord, Response.Status.UNAUTHORIZED, "Matrícula ou senha incorreta.");
        }
        loginRateLimitService.registrarSucessoMatricula(matricula);
        final String setorFinal = setor;
        boolean coordena = vinculos(s.unidade()).stream().anyMatch(v -> v.setor().equals(setorFinal)
                && v.matricula().equalsIgnoreCase(matricula) && RegrasCoordenacaoSetor.COORDENADOR.equals(v.papel()));
        boolean administracao = autorizador.hasPermission("kadm");
        if (!coordena && !administracao) {
            LOG.warnf("Autorizacao de coordenacao recusada. solicitante=%s autorizador=%s setor=%s unidade=%s",
                    s.matricula(), matricula, setor, s.unidade());
            return recusarLiberacao(s, tipoCoord, matricula, bamCoord, Response.Status.FORBIDDEN, "Esta matrícula não é coordenadora do " + setor
                    + " nesta unidade. A administração define os coordenadores em Coordenação de setor.");
        }
        LOG.infof("Autorizacao de coordenacao concedida. solicitante=%s autorizador=%s setor=%s unidade=%s como=%s",
                s.matricula(), matricula, setor, s.unidade(), coordena ? "COORDENADOR" : "ADMINISTRACAO");
        logSistema(s, "LIBERAÇÃO CONCEDIDA: " + tipoCoord + " por " + texto(autorizador.nome()) + " (matrícula " + matricula + ") como "
                + (coordena ? "COORDENADOR do " + setor : "ADMINISTRADOR (contingência, na ausência do coordenador)")
                + (bamCoord.isBlank() ? "" : " | BAM " + bamCoord), bamCoord);
        return Response.ok(Map.of("matricula", matricula, "nome", texto(autorizador.nome()),
                "papel", coordena ? "COORDENADOR" : "ADMINISTRACAO")).build();
    }

    /**
     * Liberacao do medico por outra pessoa (ex.: restaurar resumo de alta no NIR): confere a senha e o perfil
     * Medico. O validar-credencial so aceita o proprio usuario logado e por isso nao serve aqui.
     */
    @POST
    @Path("autorizar-medico")
    @Transactional
    public Response autorizarMedico(Map<String, Object> dto) {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        String matricula = texto(dto == null ? null : dto.get("matricula"));
        String senha = dto == null || dto.get("senha") == null ? "" : String.valueOf(dto.get("senha"));
        if (matricula.isBlank() || senha.isBlank()) return erro(Response.Status.BAD_REQUEST, "Informe matrícula e senha do médico.");
        String bamMed = texto(dto.get("bam"));
        String tipoMed = "liberação do médico que deu a alta";
        if (matricula.equalsIgnoreCase(s.matricula())) {
            return recusarLiberacao(s, tipoMed, matricula, bamMed, Response.Status.FORBIDDEN,
                    "A liberação do médico precisa ser de outra pessoa, não do usuário logado.");
        }
        if (loginRateLimitService.segundosBloqueadoMatricula(matricula) > 0) {
            return recusarLiberacao(s, tipoMed, matricula, bamMed, Response.Status.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.");
        }
        AuthenticatedUser medico = securityPermissionService.authenticate(matricula, senha);
        if (medico == null) {
            loginRateLimitService.registrarFalhaMatricula(matricula);
            return recusarLiberacao(s, tipoMed, matricula, bamMed, Response.Status.UNAUTHORIZED, "Matrícula ou senha incorreta.");
        }
        loginRateLimitService.registrarSucessoMatricula(matricula);

        // Quem pode liberar e o medico que deu a alta: assinatura do resumo de alta (matricula) ou, sem ela,
        // o nome registrado na alta da internacao. Na ausencia dele, a administracao libera (contingencia).
        String bam = texto(dto.get("bam"));
        String[] autorAssinatura = autorDoResumoDeAlta(bam);
        String nomeAlta = autorAssinatura == null ? medicoDaAltaNaInternacao(bam) : null;
        boolean administracao = medico.hasPermission("kadm");
        String papel;
        if (autorAssinatura != null) {
            if (autorAssinatura[0].equalsIgnoreCase(matricula)) papel = "MEDICO_DA_ALTA";
            else if (administracao) papel = "CONTINGENCIA_ADMINISTRACAO";
            else {
                return recusarMedico(s, matricula, bam,"O resumo de alta foi assinado por " + autorAssinatura[1] + " (matrícula "
                        + autorAssinatura[0] + "). Só ele pode liberar ou, na ausência dele, um administrador.");
            }
        } else if (nomeAlta != null) {
            if (mesmoNome(nomeAlta, medico.nome())) papel = "MEDICO_DA_ALTA";
            else if (administracao) papel = "CONTINGENCIA_ADMINISTRACAO";
            else {
                return recusarMedico(s, matricula, bam,"A alta foi dada por " + nomeAlta
                        + ". Só ele pode liberar ou, na ausência dele, um administrador.");
            }
        } else if (Boolean.TRUE.equals(medico.permissions().get("kmedico")) || administracao) {
            papel = "SEM_REGISTRO_DO_MEDICO_DA_ALTA";
        } else {
            return recusarMedico(s, matricula, bam,"Não há registro de quem deu a alta deste BAM; a liberação exige um médico ou administrador.");
        }
        LOG.infof("Liberacao medica concedida. solicitante=%s autorizador=%s bam=%s unidade=%s como=%s",
                s.matricula(), matricula, bam, s.unidade(), papel);
        String esperado = autorAssinatura != null ? autorAssinatura[1] + " (matrícula " + autorAssinatura[0] + ", assinou o resumo de alta)"
                : nomeAlta != null ? nomeAlta + " (registrado na alta da internação)" : "não identificado";
        String como = switch (papel) {
            case "MEDICO_DA_ALTA" -> "o MÉDICO QUE DEU A ALTA";
            case "CONTINGENCIA_ADMINISTRACAO" -> "ADMINISTRADOR (contingência, na ausência do médico da alta)";
            default -> "médico/administrador, sem registro do médico da alta";
        };
        logSistema(s, "LIBERAÇÃO CONCEDIDA: " + tipoMed + " por " + texto(medico.nome()) + " (matrícula " + matricula + ") como " + como
                + " | médico da alta: " + esperado + " | BAM " + (bam.isBlank() ? "-" : bam), bam);
        return Response.ok(Map.of("matricula", matricula, "nome", texto(medico.nome()), "papel", papel)).build();
    }

    private Response recusarMedico(Sessao s, String matricula, String bam, String motivo) {
        LOG.warnf("Liberacao medica recusada. solicitante=%s autorizador=%s unidade=%s motivo=%s", s.matricula(), matricula, s.unidade(), motivo);
        return recusarLiberacao(s, "liberação do médico que deu a alta", matricula, bam, Response.Status.FORBIDDEN, motivo);
    }

    /** Registra no log do sistema a liberação recusada (quem pediu, quem tentou liberar, motivo) e devolve o erro. */
    private Response recusarLiberacao(Sessao s, String tipo, String matricula, String bam, Response.Status status, String motivo) {
        logSistema(s, "LIBERAÇÃO RECUSADA: " + tipo + " | tentativa com " + pessoa(matricula)
                + (bam == null || bam.isBlank() ? "" : " | BAM " + bam) + " | motivo: " + motivo, bam);
        return erro(status, motivo);
    }

    /** {matricula, nome} de quem assinou o resumo de alta do BAM, ou null. */
    @SuppressWarnings("unchecked")
    private String[] autorDoResumoDeAlta(String bam) {
        if (!bam.matches("\\d{1,9}")) return null;
        try {
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT assinado_por_matricula, assinado_por FROM assinatura_clinica
                    WHERE tipo_documento = 'RESUMO_ALTA' AND idbam = :bam AND COALESCE(assinado_por_matricula, '') <> ''
                    ORDER BY assinado_em DESC NULLS LAST, idassinatura DESC LIMIT 1
                    """).setParameter("bam", Integer.parseInt(bam)).getResultList();
            return rows.isEmpty() ? null : new String[] { texto(rows.get(0)[0]), texto(rows.get(0)[1]) };
        } catch (RuntimeException e) {
            LOG.debugf("Autor do resumo de alta indisponivel para o BAM %s: %s", bam, e.getMessage());
            return null;
        }
    }

    /** Nome gravado em novointernado.quemdeualta (a internacao guarda o nome, nao a matricula), ou null. */
    @SuppressWarnings("unchecked")
    private String medicoDaAltaNaInternacao(String bam) {
        if (!bam.matches("\\d{1,9}")) return null;
        try {
            List<Object> rows = em.createNativeQuery("""
                    SELECT quemdeualta FROM novointernado
                    WHERE nbam = :bam AND COALESCE(TRIM(quemdeualta), '') <> ''
                    ORDER BY idninternado DESC LIMIT 1
                    """).setParameter("bam", Integer.parseInt(bam)).getResultList();
            return rows.isEmpty() ? null : texto(rows.get(0));
        } catch (RuntimeException e) {
            LOG.debugf("Medico da alta indisponivel para o BAM %s: %s", bam, e.getMessage());
            return null;
        }
    }

    private static String normalizarNome(String nome) {
        String semAcento = java.text.Normalizer.normalize(texto(nome), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z ]+", " ").replaceAll("\\s+", " ").trim();
    }

    /** O registro pode trazer "NOME - CRM: 123": compara so a parte do nome, sem acento e sem caixa. */
    static boolean mesmoNome(String registrado, String nome) {
        String r = normalizarNome(texto(registrado).split(" - ")[0]);
        String n = normalizarNome(nome);
        return !r.isEmpty() && !n.isEmpty() && (r.equals(n) || r.startsWith(n + " ") || n.startsWith(r + " "));
    }

    // ---- gestao: so administracao ----------------------------------------------------------------

    private Response exigirAdministracao(Sessao s) {
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        if (!s.administrador()) return erro(Response.Status.FORBIDDEN, "Somente a administração define coordenadores e equipes.");
        return null;
    }

    @GET
    @Path("gestao")
    @Transactional
    public Response gestao() {
        Sessao s = sessao();
        Response negado = exigirAdministracao(s);
        if (negado != null) return negado;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Vinculo v : vinculos(s.unidade())) {
            Servidor2 s2 = servidor2Repository.localizarMatricula(v.matricula());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id()); m.put("setor", v.setor()); m.put("matricula", v.matricula());
            m.put("nome", s2 == null ? "" : texto(s2.getNome())); m.put("papel", v.papel());
            out.add(m);
        }
        return Response.ok(Map.of("unidade", s.unidade(), "vinculos", out)).build();
    }

    /** Inclui alguem como coordenador ou membro do setor; se ja estiver no setor, troca o papel. */
    @POST
    @Path("gestao")
    @Transactional
    public Response incluir(Map<String, Object> dto) {
        Sessao s = sessao();
        Response negado = exigirAdministracao(s);
        if (negado != null) return negado;
        String setor = texto(dto == null ? null : dto.get("setor"));
        String matricula = texto(dto == null ? null : dto.get("matricula"));
        String papel = texto(dto == null ? null : dto.get("papel")).toUpperCase();
        if (setor.isBlank() || setor.length() > 120 || matricula.isBlank() || matricula.length() > 15
                || !(RegrasCoordenacaoSetor.COORDENADOR.equals(papel) || RegrasCoordenacaoSetor.MEMBRO.equals(papel))) {
            return erro(Response.Status.BAD_REQUEST, "Informe setor, matrícula e papel (coordenador ou membro).");
        }
        Servidor2 s2 = servidor2Repository.localizarMatricula(matricula);
        if (s2 == null) return erro(Response.Status.NOT_FOUND, "Matrícula sem cadastro de acessos.");
        // Mesma regra da revisão automática: sem isso a inclusão seria desfeita na próxima leitura, sem aviso.
        String impedimento = motivoPerdaVinculo(matricula, s.unidade(), papel);
        if (impedimento != null) {
            logSistema(s, "TENTATIVA RECUSADA de incluir " + pessoa(matricula) + " no setor " + setor + " | motivo: " + impedimento, null);
            return erro(Response.Status.BAD_REQUEST, "Não é possível incluir: " + impedimento + ".");
        }
        int trocados = em.createNativeQuery("""
                UPDATE setor_coordenacao SET papel = :p
                WHERE unidade = :u AND setor = :s AND matricula = :m AND removido_em IS NULL
                """).setParameter("p", papel).setParameter("u", s.unidade()).setParameter("s", setor)
                .setParameter("m", matricula).executeUpdate();
        if (trocados == 0) {
            em.createNativeQuery("""
                    INSERT INTO setor_coordenacao (unidade, setor, matricula, papel, incluido_por)
                    VALUES (:u, :s, :m, :p, :autor)
                    """).setParameter("u", s.unidade()).setParameter("s", setor).setParameter("m", matricula)
                    .setParameter("p", papel).setParameter("autor", s.matricula()).executeUpdate();
        }
        securityPermissionService.invalidateUserCache(matricula);
        LOG.infof("Coordenacao de setor: %s incluiu %s como %s em %s/%s", s.matricula(), matricula, papel, s.unidade(), setor);
        logSistema(s, (trocados > 0 ? "ALTEROU O PAPEL de " : "INCLUIU ") + pessoa(matricula) + " como "
                + (RegrasCoordenacaoSetor.COORDENADOR.equals(papel) ? "COORDENADOR" : "EQUIPE") + " do setor " + setor, null);
        return Response.ok(Map.of("setor", setor, "matricula", matricula, "nome", texto(s2.getNome()), "papel", papel)).build();
    }

    @POST
    @Path("gestao/{id}/remover")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response remover(@PathParam("id") long id) {
        Sessao s = sessao();
        Response negado = exigirAdministracao(s);
        if (negado != null) return negado;
        List<Object[]> vinculo = em.createNativeQuery(
                "SELECT matricula, setor, papel FROM setor_coordenacao WHERE id = :id AND unidade = :u AND removido_em IS NULL")
                .setParameter("id", id).setParameter("u", s.unidade()).getResultList();
        if (vinculo.isEmpty()) return erro(Response.Status.NOT_FOUND, "Vínculo não encontrado nesta unidade.");
        em.createNativeQuery("UPDATE setor_coordenacao SET removido_em = now(), removido_por = :autor WHERE id = :id")
                .setParameter("autor", s.matricula()).setParameter("id", id).executeUpdate();
        String matriculaRemovida = texto(vinculo.get(0)[0]);
        securityPermissionService.invalidateUserCache(matriculaRemovida);
        logSistema(s, "RETIROU " + pessoa(matriculaRemovida) + " do setor " + texto(vinculo.get(0)[1]) + " (era "
                + (RegrasCoordenacaoSetor.COORDENADOR.equals(texto(vinculo.get(0)[2])) ? "COORDENADOR" : "EQUIPE")
                + "). Os acessos atuais da pessoa não foram alterados", null);
        return Response.ok(Map.of("id", id, "removido", true)).build();
    }
}
