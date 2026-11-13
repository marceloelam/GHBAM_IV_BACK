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
        // Sai da equipe sem levar o setor junto: desliga os mesmos interruptores que o painel mostra (perfil do grupo e
        // acessos do menu do setor que quem retira gerencia), cada um auditado com a justificativa da retirada.
        List<String> desligados = desligarAcessosDoSetor(s, setor, matricula, justificativa);
        limparItens(s.unidade(), setor, matricula, s.matricula());
        em.createNativeQuery("UPDATE setor_coordenacao SET removido_em = now(), removido_por = :autor WHERE id = :id AND removido_em IS NULL")
                .setParameter("autor", s.matricula()).setParameter("id", alvo.id()).executeUpdate();
        securityPermissionService.invalidateUserCache(matricula);
        logSistema(s, "RETIROU " + pessoa(matricula) + " da equipe do setor " + setor + (s.administrador() ? "" : " (pela coordenação)")
                + " | justificativa: " + justificativa + " | acessos do setor desligados: "
                + (desligados.isEmpty() ? "nenhum estava ligado" : String.join(", ", desligados)), null);
        return Response.ok(Map.of("setor", setor, "matricula", matricula, "removido", true, "desligados", desligados)).build();
    }

    /**
     * Servidores da unidade da sessão que o coordenador pode chamar para a equipe, com os setores em que já estão.
     * A lista geral (servidor2/unidade) é só da administração; aqui vão apenas matrícula e nome.
     */
    @GET
    @Path("equipes/candidatos")
    @Transactional
    public Response candidatos() {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        List<Vinculo> vinculos = vinculos(s.unidade());
        if (!s.administrador() && setoresCoordenados(vinculos, s).isEmpty()) {
            return erro(Response.Status.FORBIDDEN, "Você não coordena nenhum setor nesta unidade.");
        }
        Map<String, List<String>> ondeEsta = new LinkedHashMap<>();
        for (Vinculo v : vinculos) {
            ondeEsta.computeIfAbsent(v.matricula().toUpperCase(), k -> new ArrayList<>())
                    .add(v.setor() + " (" + (RegrasCoordenacaoSetor.COORDENADOR.equals(v.papel()) ? "Coordenador" : "Equipe") + ")");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Servidor2 s2 : servidor2Repository.filtarPorUnidadeDoServidor2(s.unidade())) {
            // "unidade like X%" também pega unidades com o mesmo prefixo: confere a sigla exata.
            if (!texto(s2.getUnidade()).split(":")[0].trim().equalsIgnoreCase(s.unidade())) continue;
            String matricula = texto(s2.getMatricula());
            if (matricula.isBlank()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matricula", matricula);
            m.put("nome", texto(s2.getNome()));
            m.put("ondeEsta", ondeEsta.getOrDefault(matricula.toUpperCase(), List.of()));
            out.add(m);
        }
        return Response.ok(out).build();
    }

    /** O coordenador inclui alguém na equipe do setor que coordena (sempre como membro; coordenador é pela administração). */
    @POST
    @Path("equipes/incluir")
    @Transactional
    public Response incluirNaEquipe(Map<String, Object> dto) {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        String setor = texto(dto == null ? null : dto.get("setor"));
        String matricula = texto(dto == null ? null : dto.get("matricula"));
        if (setor.isBlank() || matricula.isBlank() || matricula.length() > 15) return erro(Response.Status.BAD_REQUEST, "Informe o setor e o servidor.");
        List<Vinculo> vinculos = vinculos(s.unidade());
        String recusa = null;
        if (!s.administrador() && !setoresCoordenados(vinculos, s).contains(setor)) recusa = "Você não coordena este setor.";
        else if (matricula.equalsIgnoreCase(s.matricula())) recusa = "Você não pode incluir a si mesmo.";
        // Quem já está em algum setor (inclusive como coordenador) não entra em outro: a inclusão zera os acessos.
        else if (vinculos.stream().anyMatch(v -> v.matricula().equalsIgnoreCase(matricula))) {
            recusa = "O servidor já faz parte de um setor. Ele precisa ser retirado de lá antes.";
        } else if (servidor2Repository.localizarMatricula(matricula) == null) recusa = "Matrícula sem cadastro de acessos.";
        else {
            String impedimento = motivoPerdaVinculo(matricula, s.unidade(), RegrasCoordenacaoSetor.MEMBRO);
            if (impedimento != null) recusa = "Não é possível incluir: " + impedimento + ".";
        }
        Servidor alvoServidor = recusa == null ? servidorRepository.localizarMatricula(matricula) : null;
        Servidor2 alvoAcessos = recusa == null ? servidor2Repository.localizarMatricula(matricula) : null;
        if (recusa == null && (alvoServidor == null
                || Boolean.TRUE.equals(alvoServidor.getAdministracao()) || Boolean.TRUE.equals(alvoServidor.getSubadm())
                || Boolean.TRUE.equals(alvoAcessos.getSuperusuario()))) {
            recusa = "Servidor da administração: só a administração altera os acessos dele.";
        }
        if (recusa != null) {
            logSistema(s, "TENTATIVA RECUSADA de incluir " + pessoa(matricula) + " na equipe do setor " + setor + " | motivo: " + recusa, null);
            return erro(Response.Status.FORBIDDEN, recusa);
        }
        em.createNativeQuery("""
                INSERT INTO setor_coordenacao (unidade, setor, matricula, papel, incluido_por)
                VALUES (:u, :s, :m, :p, :autor)
                """).setParameter("u", s.unidade()).setParameter("s", setor).setParameter("m", matricula)
                .setParameter("p", RegrasCoordenacaoSetor.MEMBRO).setParameter("autor", s.matricula()).executeUpdate();
        // Entra em branco: todos os acessos e perfis concedíveis são desligados (um registro de auditoria por chave)
        // e o coordenador liga o que a pessoa vai usar. Categoria do cargo e administração não são tocados.
        List<String> zerados;
        try {
            zerados = zerarAcessos(s, setor, matricula, alvoServidor, alvoAcessos);
            em.flush();
        } catch (RuntimeException e) {
            // Desfaz a inclusão (a exceção derruba a transação) e mostra o motivo na tela, em vez de um 500 mudo.
            Throwable causa = e;
            while (causa.getCause() != null && causa.getCause() != causa) causa = causa.getCause();
            LOG.errorf(e, "Coordenacao de setor: falha ao zerar acessos de %s no setor %s", matricula, setor);
            throw new jakarta.ws.rs.WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("erro", "Não foi possível incluir na equipe: " + texto(causa.getMessage()))).build());
        }
        securityPermissionService.invalidateUserCache(matricula);
        logSistema(s, "INCLUIU " + pessoa(matricula) + " na equipe do setor " + setor + (s.administrador() ? "" : " (pela coordenação)")
                + " | acessos anteriores desligados (" + zerados.size() + "): "
                + (zerados.isEmpty() ? "nenhum estava ligado" : String.join(", ", zerados)), null);
        return Response.ok(Map.of("setor", setor, "matricula", matricula, "nome", texto(alvoAcessos.getNome()), "zerados", zerados)).build();
    }

    private static final String JUSTIFICATIVA_INCLUSAO = "Inclusão na equipe do setor: acessos anteriores desligados";

    private List<String> zerarAcessos(Sessao s, String setor, String matricula, Servidor alvoServidor, Servidor2 alvoAcessos) {
        List<String> perfis = RegrasCoordenacaoSetor.perfisConcediveis().stream().sorted()
                .filter(p -> Boolean.TRUE.equals(RegrasCoordenacaoSetor.valorPerfil(alvoServidor, p))).toList();
        List<String> acessos = RegrasCoordenacaoSetor.chavesServidor2().stream()
                .filter(c -> Boolean.TRUE.equals(RegrasCoordenacaoSetor.valor(alvoAcessos, c))).toList();
        // Colunas vindas das listas fixas/da entidade, nunca do navegador.
        if (!perfis.isEmpty()) {
            em.createNativeQuery("UPDATE servidor SET " + String.join(", ", perfis.stream().map(p -> p + " = false").toList())
                    + " WHERE idservidor = :id").setParameter("id", alvoServidor.getIdservidor()).executeUpdate();
        }
        if (!acessos.isEmpty()) {
            em.createNativeQuery("UPDATE servidor2 SET " + String.join(", ", acessos.stream().map(c -> c + " = false").toList())
                    + " WHERE idservidor2 = :id").setParameter("id", alvoAcessos.getIdservidor2()).executeUpdate();
        }
        List<String> zerados = new ArrayList<>();
        perfis.forEach(p -> zerados.add(RegrasCoordenacaoSetor.PREFIXO_PERFIL + p));
        zerados.addAll(acessos);
        for (String chave : zerados) registrarAuditoria(s, setor, matricula, chave, Boolean.TRUE, false, JUSTIFICATIVA_INCLUSAO);
        return zerados;
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
            return Response.ok(Map.of("unidade", s.unidade(), "restritos", List.of(), "equipes", List.of(),
                    "itensOcultos", itensOcultos(s.matricula()))).build();
        }
        List<Vinculo> vinculos = vinculos(s.unidade());
        Set<String> comCoordenador = new LinkedHashSet<>();
        Set<String> minhas = new LinkedHashSet<>();
        for (Vinculo v : vinculos) {
            if (RegrasCoordenacaoSetor.COORDENADOR.equals(v.papel())) comCoordenador.add(v.setor());
            if (v.matricula().equalsIgnoreCase(s.matricula())) minhas.add(v.setor());
        }
        // Decisão do Marcelo (11/11/2026): quem está fora da equipe segue só o que a administração definiu em
        // /servidores; ter coordenador no setor não fecha o menu a ninguém. A coordenação vale só para os membros.
        List<String> restritos = List.of();
        // Membro (não coordenador) só vê, no grupo do setor, os itens que o coordenador liberou.
        // Setor sem coordenador ativo: vale só o cadastro feito pela administração (sem filtro de itens).
        Map<String, List<String>> itens = new LinkedHashMap<>();
        for (Vinculo v : vinculos) {
            if (v.matricula().equalsIgnoreCase(s.matricula()) && RegrasCoordenacaoSetor.MEMBRO.equals(v.papel())
                    && comCoordenador.contains(v.setor())) {
                itens.put(v.setor(), itensLiberados(s.unidade(), v.setor(), s.matricula()));
            }
        }
        return Response.ok(Map.of("unidade", s.unidade(), "restritos", restritos, "equipes", List.copyOf(minhas),
                "itensLiberados", itens, "itensOcultos", itensOcultos(s.matricula()))).build();
    }

    /** { grupo: [rotas] } que a administração escondeu deste servidor na tela de servidores. */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> itensOcultos(String matricula) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try {
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT grupo, rota FROM servidor_menu_item_oculto
                    WHERE matricula = :m AND removido_em IS NULL ORDER BY grupo, rota
                    """).setParameter("m", matricula).getResultList();
            for (Object[] r : rows) out.computeIfAbsent(texto(r[0]), k -> new ArrayList<>()).add(texto(r[1]));
        } catch (RuntimeException e) {
            // Tabela ainda não migrada: nada escondido.
        }
        return out;
    }

    /** Acesso ligado e visível no menu deste setor (não desligado só nele). Mesma regra de acessoPorMenu.js. */
    private static boolean visivelNoSetor(Servidor2 s2, String chave, String setor, Map<String, List<String>> ocultos) {
        if (s2 == null || !Boolean.TRUE.equals(RegrasCoordenacaoSetor.valor(s2, chave))) return false;
        List<String> rotas = RegrasCoordenacaoSetor.rotasDoAcesso(chave).getOrDefault(setor, List.of());
        return rotas.isEmpty() || !ocultos.getOrDefault(setor, List.of()).containsAll(rotas);
    }

    private void ocultarRotas(String matricula, String grupo, List<String> rotas, boolean ocultar, String autor,
                              Map<String, List<String>> ocultos) {
        List<String> atuais = ocultos.computeIfAbsent(grupo, g -> new ArrayList<>());
        for (String rota : rotas) {
            if (ocultar == atuais.contains(rota)) continue;
            if (ocultar) {
                em.createNativeQuery("INSERT INTO servidor_menu_item_oculto (matricula, grupo, rota, incluido_por) VALUES (:m, :g, :r, :autor)")
                        .setParameter("m", matricula).setParameter("g", grupo).setParameter("r", rota).setParameter("autor", autor).executeUpdate();
                atuais.add(rota);
            } else {
                em.createNativeQuery("""
                        UPDATE servidor_menu_item_oculto SET removido_em = now(), removido_por = :autor
                        WHERE matricula = :m AND grupo = :g AND rota = :r AND removido_em IS NULL
                        """).setParameter("autor", autor).setParameter("m", matricula).setParameter("g", grupo)
                        .setParameter("r", rota).executeUpdate();
                atuais.remove(rota);
            }
        }
    }

    /**
     * Liga/desliga o acesso só no menu do setor. Ligar: chave ligada e item visível no setor; se a chave estava
     * desligada, os outros grupos ficam ocultos. Desligar: oculta no setor; sem grupo visível, desliga a chave.
     * Devolve o valor anterior no setor, ou null se nada mudou.
     */
    private Boolean alterarNoSetor(String setor, String matricula, Servidor2 alvo, String chave, boolean valor, String autor) {
        Map<String, List<String>> ocultos = new LinkedHashMap<>();
        itensOcultos(matricula).forEach((g, l) -> ocultos.put(g, new ArrayList<>(l)));
        boolean anterior = visivelNoSetor(alvo, chave, setor, ocultos);
        if (anterior == valor) return null;
        boolean chaveLigada = Boolean.TRUE.equals(RegrasCoordenacaoSetor.valor(alvo, chave));
        Map<String, List<String>> rotas = RegrasCoordenacaoSetor.rotasDoAcesso(chave);
        List<String> doSetor = rotas.getOrDefault(setor, List.of());
        // A coluna vem da lista de campos da entidade (validada por quem chama), nunca do texto do navegador.
        if (doSetor.isEmpty()) {
            em.createNativeQuery("UPDATE servidor2 SET " + chave + " = :v WHERE idservidor2 = :id")
                    .setParameter("v", valor).setParameter("id", alvo.getIdservidor2()).executeUpdate();
        } else if (valor) {
            if (!chaveLigada) {
                em.createNativeQuery("UPDATE servidor2 SET " + chave + " = true WHERE idservidor2 = :id")
                        .setParameter("id", alvo.getIdservidor2()).executeUpdate();
                rotas.forEach((g, r) -> { if (!g.equals(setor)) ocultarRotas(matricula, g, r, true, autor, ocultos); });
            }
            ocultarRotas(matricula, setor, doSetor, false, autor, ocultos);
        } else {
            ocultarRotas(matricula, setor, doSetor, true, autor, ocultos);
            boolean visivelEmOutro = rotas.entrySet().stream()
                    .anyMatch(e -> !ocultos.getOrDefault(e.getKey(), List.of()).containsAll(e.getValue()));
            if (!visivelEmOutro) {
                em.createNativeQuery("UPDATE servidor2 SET " + chave + " = false WHERE idservidor2 = :id")
                        .setParameter("id", alvo.getIdservidor2()).executeUpdate();
                rotas.forEach((g, r) -> ocultarRotas(matricula, g, r, false, autor, ocultos));
            }
        }
        securityPermissionService.invalidateUserCache(matricula);
        return anterior;
    }

    /** Administração: itens do menu escondidos de um servidor. */
    @GET
    @Path("gestao/menu-itens/{matricula}")
    @Transactional
    public Response menuItensDoServidor(@PathParam("matricula") String matricula) {
        Sessao s = sessao();
        Response negado = exigirAdministracao(s);
        if (negado != null) return negado;
        if (texto(matricula).isBlank() || matricula.length() > 15) return erro(Response.Status.BAD_REQUEST, "Matrícula inválida.");
        return Response.ok(Map.of("matricula", matricula, "ocultos", itensOcultos(texto(matricula)))).build();
    }

    /** Administração: esconde ou volta a mostrar um item do menu para um servidor. Corpo: grupo, rota, nome, visivel. */
    @PUT
    @Path("gestao/menu-itens/{matricula}")
    @Transactional
    public Response alterarMenuItemDoServidor(@PathParam("matricula") String matriculaBruta, Map<String, Object> dto) {
        Sessao s = sessao();
        Response negado = exigirAdministracao(s);
        if (negado != null) return negado;
        String matricula = texto(matriculaBruta);
        String grupo = texto(dto == null ? null : dto.get("grupo"));
        String rota = texto(dto == null ? null : dto.get("rota"));
        Object bruto = dto == null ? null : dto.get("visivel");
        if (matricula.isBlank() || matricula.length() > 15 || grupo.isBlank() || grupo.length() > 120
                || !ROTA_VALIDA.matcher(rota).matches() || !(bruto instanceof Boolean)) {
            return erro(Response.Status.BAD_REQUEST, "Informe servidor, grupo do menu, item e se fica visível.");
        }
        if (servidor2Repository.localizarMatricula(matricula) == null) return erro(Response.Status.NOT_FOUND, "Matrícula sem cadastro de acessos.");
        boolean visivel = (Boolean) bruto;
        boolean oculto = itensOcultos(matricula).getOrDefault(grupo, List.of()).contains(rota);
        if (oculto != visivel) return Response.ok(Map.of("rota", rota, "visivel", visivel, "alterado", false)).build();
        if (!visivel) {
            em.createNativeQuery("""
                    INSERT INTO servidor_menu_item_oculto (matricula, grupo, rota, incluido_por) VALUES (:m, :g, :r, :autor)
                    """).setParameter("m", matricula).setParameter("g", grupo).setParameter("r", rota)
                    .setParameter("autor", s.matricula()).executeUpdate();
        } else {
            em.createNativeQuery("""
                    UPDATE servidor_menu_item_oculto SET removido_em = now(), removido_por = :autor
                    WHERE matricula = :m AND grupo = :g AND rota = :r AND removido_em IS NULL
                    """).setParameter("autor", s.matricula()).setParameter("m", matricula).setParameter("g", grupo)
                    .setParameter("r", rota).executeUpdate();
        }
        securityPermissionService.invalidateUserCache(matricula);
        String nome = texto(dto.get("nome"));
        logSistema(s, (visivel ? "MOSTROU" : "ESCONDEU") + " o item '" + (nome.isBlank() ? rota : nome) + "' (" + rota + ") do menu "
                + grupo + " para " + pessoa(matricula), null);
        return Response.ok(Map.of("rota", rota, "visivel", visivel, "alterado", true)).build();
    }

    @SuppressWarnings("unchecked")
    private List<String> itensLiberados(String unidade, String setor, String matricula) {
        try {
            return ((List<Object>) em.createNativeQuery("""
                    SELECT rota FROM setor_membro_item
                    WHERE unidade = :u AND setor = :s AND matricula = :m AND removido_em IS NULL ORDER BY rota
                    """).setParameter("u", unidade).setParameter("s", setor).setParameter("m", matricula)
                    .getResultList()).stream().map(String::valueOf).toList();
        } catch (RuntimeException e) {
            // Tabela ainda não migrada: sem itens liberados (o membro vê o grupo em branco).
            return List.of();
        }
    }

    private void limparItens(String unidade, String setor, String matricula, String autor) {
        em.createNativeQuery("""
                UPDATE setor_membro_item SET removido_em = now(), removido_por = :autor
                WHERE unidade = :u AND setor = :s AND matricula = :m AND removido_em IS NULL
                """).setParameter("autor", autor).setParameter("u", unidade).setParameter("s", setor)
                .setParameter("m", matricula).executeUpdate();
    }

    private static final java.util.regex.Pattern ROTA_VALIDA = java.util.regex.Pattern.compile("^/[A-Za-z0-9_\\-/]{1,199}$");

    /** Libera ou retira um item do menu do setor para um membro. Corpo: setor, matricula, rota, valor. */
    @PUT
    @Path("equipes/item")
    @Transactional
    public Response alterarItem(Map<String, Object> dto) {
        Sessao s = sessao();
        if (s == null) return erro(Response.Status.UNAUTHORIZED, "Unidade ou profissional não identificados na sessão.");
        String setor = texto(dto == null ? null : dto.get("setor"));
        String matricula = texto(dto == null ? null : dto.get("matricula"));
        String rota = texto(dto == null ? null : dto.get("rota"));
        Object bruto = dto == null ? null : dto.get("valor");
        if (setor.isBlank() || matricula.isBlank() || !ROTA_VALIDA.matcher(rota).matches() || !(bruto instanceof Boolean)) {
            return erro(Response.Status.BAD_REQUEST, "Informe setor, servidor, item do menu e o novo valor.");
        }
        boolean valor = (Boolean) bruto;
        List<Vinculo> vinculos = vinculos(s.unidade());
        boolean coordena = setoresCoordenados(vinculos, s).contains(setor);
        String papelAlvo = vinculos.stream()
                .filter(v -> v.setor().equals(setor) && v.matricula().equalsIgnoreCase(matricula))
                .map(Vinculo::papel).findFirst().orElse(null);
        String recusa = RegrasCoordenacaoSetor.recusaVinculo(s.administrador(), coordena, papelAlvo, s.matricula(), matricula);
        if (recusa != null) {
            logSistema(s, "TENTATIVA RECUSADA de " + (valor ? "liberar" : "retirar") + " o item " + rota + " de "
                    + pessoa(matricula) + " no setor " + setor + " | motivo: " + recusa, null);
            return erro(Response.Status.FORBIDDEN, recusa);
        }
        boolean anterior = itensLiberados(s.unidade(), setor, matricula).contains(rota);
        if (anterior == valor) return Response.ok(Map.of("rota", rota, "valor", valor, "alterado", false)).build();
        if (valor) {
            em.createNativeQuery("""
                    INSERT INTO setor_membro_item (unidade, setor, matricula, rota, incluido_por) VALUES (:u, :s, :m, :r, :autor)
                    """).setParameter("u", s.unidade()).setParameter("s", setor).setParameter("m", matricula)
                    .setParameter("r", rota).setParameter("autor", s.matricula()).executeUpdate();
        } else {
            em.createNativeQuery("""
                    UPDATE setor_membro_item SET removido_em = now(), removido_por = :autor
                    WHERE unidade = :u AND setor = :s AND matricula = :m AND rota = :r AND removido_em IS NULL
                    """).setParameter("autor", s.matricula()).setParameter("u", s.unidade()).setParameter("s", setor)
                    .setParameter("m", matricula).setParameter("r", rota).executeUpdate();
        }
        String nomeItem = texto(dto.get("nome"));
        auditar(s, setor, matricula, "item:" + rota, anterior, valor, nomeItem.isBlank() ? null : "Item do menu: " + nomeItem);
        return Response.ok(Map.of("rota", rota, "valor", valor, "alterado", true)).build();
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

    /**
     * Acessos que a coordenação liga e desliga: tudo, menos os reservados à administração. Decisão do Marcelo
     * (11/11/2026): "tudo que tem no menu pode ser habilitado ou não" — o coordenador não precisa ter o acesso.
     * O filtro por setor (só os itens do menu do grupo) é feito em acessosDoSetor.
     */
    /** Todas as colunas de acesso, menos superusuário: o filtro pelo menu do setor vem de acessosDoSetor. */
    private static List<String> itensDoMenu() {
        return RegrasCoordenacaoSetor.chavesServidor2().stream().filter(c -> !"superusuario".equals(c)).toList();
    }

    private List<String> concediveis(Sessao s) {
        return RegrasCoordenacaoSetor.concediveis(RegrasCoordenacaoSetor.chavesServidor2(), Set.of(), true);
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

        List<Map<String, Object>> lista = new ArrayList<>();
        for (String setor : setores) {
            // Cada setor (grupo do menu) concede so os acessos e perfis que abrem aquele grupo.
            // Mesma lista da tela de servidores: só os itens do menu do grupo, todos alteráveis pela coordenação.
            List<String> chavesSetor = RegrasCoordenacaoSetor.acessosDoSetor(setor, new ArrayList<>(RegrasCoordenacaoSetor.chavesServidor2()));
            // Perfil do grupo: a coordenação liga mesmo sem tê-lo (a regra do cargo continua valendo).
            List<String> perfisSetor = RegrasCoordenacaoSetor.perfisDoSetor(setor);
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
                    Map<String, List<String>> ocultosMembro = itensOcultos(v.matricula());
                    for (String chave : chavesSetor) {
                        valores.put(chave, visivelNoSetor(s2, chave, setor, ocultosMembro));
                    }
                    pessoa.put("valores", valores);
                    pessoa.put("bloqueios", bloqueios);
                    pessoa.put("itens", itensLiberados(s.unidade(), setor, v.matricula()));
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
        // Item do menu do setor: a coordenação liga e desliga, mesmo os reservados (decisão do Marcelo, 11/11/2026).
        // A chave precisa ser coluna de servidor2 (vai para o UPDATE); superusuário nunca.
        String recusa = RegrasCoordenacaoSetor.recusaVinculo(s.administrador(), coordena, papelAlvo, s.matricula(), matricula);
        if (recusa == null && (!RegrasCoordenacaoSetor.chavesServidor2().contains(chave) || "superusuario".equals(chave))) {
            recusa = "Acesso inválido.";
        }
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
        // Só no menu deste setor (cada menu liga o seu).
        Boolean anterior = alterarNoSetor(setor, matricula, alvo, chave, valor, s.matricula());
        if (anterior == null) {
            return Response.ok(Map.of("matricula", matricula, "chave", chave, "valor", valor, "alterado", false)).build();
        }
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
        // Mesmo modelo da tela de servidores (Marcelo, 11/11/2026): desligar o perfil desliga os itens do menu do
        // setor (só neste menu); ligar não liga nada.
        List<String> desligados = new ArrayList<>();
        Servidor2 acessos = valor ? null : servidor2Repository.localizarMatricula(matricula);
        if (acessos != null) {
            for (String item : RegrasCoordenacaoSetor.acessosDoSetor(setor, itensDoMenu())) {
                if (alterarNoSetor(setor, matricula, acessos, item, false, s.matricula()) == null) continue;
                auditar(s, setor, matricula, item, Boolean.TRUE, false, "Perfil " + perfil + " desligado: itens do menu " + setor);
                desligados.add(item);
            }
        }
        return Response.ok(Map.of("matricula", matricula, "chave", chave, "valor", valor, "alterado", true, "desligados", desligados)).build();
    }

    /**
     * Desliga o perfil e os acessos do setor que estão ligados para o servidor, restrito ao que quem retira
     * gerencia (mesma lista do painel da equipe). Desligar não tem regra de cargo. Devolve as chaves desligadas.
     */
    private List<String> desligarAcessosDoSetor(Sessao s, String setor, String matricula, String justificativa) {
        List<String> desligados = new ArrayList<>();
        Servidor alvoServidor = servidorRepository.localizarMatricula(matricula);
        if (alvoServidor != null) {
            for (String perfil : RegrasCoordenacaoSetor.perfisDoSetor(setor)) {
                if (!Boolean.TRUE.equals(RegrasCoordenacaoSetor.valorPerfil(alvoServidor, perfil))) continue;
                // A coluna vem de perfisDoSetor (lista fixa), nunca do navegador.
                em.createNativeQuery("UPDATE servidor SET " + perfil + " = false WHERE idservidor = :id")
                        .setParameter("id", alvoServidor.getIdservidor()).executeUpdate();
                String chave = RegrasCoordenacaoSetor.PREFIXO_PERFIL + perfil;
                auditar(s, setor, matricula, chave, Boolean.TRUE, false, justificativa);
                desligados.add(chave);
            }
        }
        Servidor2 alvo = servidor2Repository.localizarMatricula(matricula);
        if (alvo != null) {
            for (String chave : RegrasCoordenacaoSetor.acessosDoSetor(setor, itensDoMenu())) {
                // Só no menu deste setor: o que está ligado em outro menu continua lá.
                if (alterarNoSetor(setor, matricula, alvo, chave, false, s.matricula()) == null) continue;
                auditar(s, setor, matricula, chave, Boolean.TRUE, false, justificativa);
                desligados.add(chave);
            }
        }
        return desligados;
    }

    private void auditar(Sessao s, String setor, String matricula, String chave, Boolean anterior, boolean valor, String justificativa) {
        registrarAuditoria(s, setor, matricula, chave, anterior, valor, justificativa);
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

    /** Linha na trilha setor_permissao_auditoria (sem log do sistema: quem chama em lote registra um só). */
    private void registrarAuditoria(Sessao s, String setor, String matricula, String chave, Boolean anterior, boolean valor, String justificativa) {
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
        // Já em alguma equipe da unidade: não zera de novo (apagaria o que o outro coordenador ligou).
        boolean primeiraEquipe = vinculos(s.unidade()).stream().noneMatch(v -> v.matricula().equalsIgnoreCase(matricula));
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
        // Membro novo entra em branco, como na inclusão pelo coordenador (antes esta porta mantinha os acessos antigos).
        // Não zera: troca de papel, coordenador, e servidor da administração/superusuário.
        List<String> zerados = List.of();
        if (trocados == 0 && primeiraEquipe && RegrasCoordenacaoSetor.MEMBRO.equals(papel)) {
            Servidor alvoServidor = servidorRepository.localizarMatricula(matricula);
            boolean daAdministracao = alvoServidor == null
                    || Boolean.TRUE.equals(alvoServidor.getAdministracao()) || Boolean.TRUE.equals(alvoServidor.getSubadm())
                    || Boolean.TRUE.equals(s2.getSuperusuario());
            if (!daAdministracao) {
                zerados = zerarAcessos(s, setor, matricula, alvoServidor, s2);
                em.flush();
            }
        }
        securityPermissionService.invalidateUserCache(matricula);
        LOG.infof("Coordenacao de setor: %s incluiu %s como %s em %s/%s", s.matricula(), matricula, papel, s.unidade(), setor);
        logSistema(s, (trocados > 0 ? "ALTEROU O PAPEL de " : "INCLUIU ") + pessoa(matricula) + " como "
                + (RegrasCoordenacaoSetor.COORDENADOR.equals(papel) ? "COORDENADOR" : "EQUIPE") + " do setor " + setor
                + (zerados.isEmpty() ? "" : " | acessos anteriores desligados (" + zerados.size() + "): " + String.join(", ", zerados)), null);
        return Response.ok(Map.of("setor", setor, "matricula", matricula, "nome", texto(s2.getNome()), "papel", papel, "zerados", zerados)).build();
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
        limparItens(s.unidade(), texto(vinculo.get(0)[1]), matriculaRemovida, s.matricula());
        securityPermissionService.invalidateUserCache(matriculaRemovida);
        logSistema(s, "RETIROU " + pessoa(matriculaRemovida) + " do setor " + texto(vinculo.get(0)[1]) + " (era "
                + (RegrasCoordenacaoSetor.COORDENADOR.equals(texto(vinculo.get(0)[2])) ? "COORDENADOR" : "EQUIPE")
                + "). Os acessos atuais da pessoa não foram alterados", null);
        return Response.ok(Map.of("id", id, "removido", true)).build();
    }
}
