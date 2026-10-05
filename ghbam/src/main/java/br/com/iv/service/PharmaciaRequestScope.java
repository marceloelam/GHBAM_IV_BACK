package br.com.iv.service;

import java.util.List;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;

@ApplicationScoped
public class PharmaciaRequestScope {
    // Areas da farmacia liberadas uma a uma em servidores2 (V193).
    public static final String PRODUTOS = "pfarmaciaprodutos";
    public static final String ESTOQUE = "pfarmaciaestoque";
    public static final String DISPENSACAO = "pfarmaciadispensacao";
    public static final String PERMUTAS = "pfarmaciapermutas";
    public static final String OPERACOES = "pfarmaciaoperacoes";
    public static final String AVALIACAO = "pfarmaciaavaliacao";
    public static final String RELATORIOS = "pfarmaciarelatorios";
    static final List<String> AREAS = List.of(PRODUTOS, ESTOQUE, DISPENSACAO, PERMUTAS, OPERACOES, AVALIACAO, RELATORIOS);
    // Perfis amplos de antes das permissoes por area: continuam com acesso total.
    static final List<String> ACESSO_TOTAL = List.of("kadm", "kfarmaciacentral", "kremedios");

    @Inject SecurityPermissionService security;

    public String unidadeAtual(ContainerRequestContext request) {
        AuthenticatedUser user = security.resolve(request);
        if (user == null) throw new NotAuthorizedException("Bearer");
        String unidade = security.unidadeDaSessao(request);
        return unidade == null || unidade.isBlank() ? user.unidade() : unidade;
    }

    /** Qualquer acesso a farmacia (leituras): perfil amplo, legado da dispensacao ou alguma area. */
    public AuthenticatedUser exigirUnidade(ContainerRequestContext request, String unidade) {
        AuthenticatedUser user = security.resolve(request);
        if (user == null) throw new NotAuthorizedException("Bearer");
        if (!(temAcessoTotal(user) || user.hasPermission("kfarmhmsfx") || AREAS.stream().anyMatch(user::hasPermission))) {
            throw new ForbiddenException();
        }
        String sessao = security.unidadeDaSessao(request);
        if (sessao == null || sessao.isBlank()) sessao = user.unidade();
        if (unidade == null || unidade.isBlank() || sessao == null || !sessao.equals(unidade)) {
            throw new ForbiddenException("A operacao deve pertencer a unidade da sessao.");
        }
        return user;
    }

    /** Gravacao numa area especifica da farmacia, alem da unidade da sessao. */
    public AuthenticatedUser exigirArea(ContainerRequestContext request, String unidade, String area) {
        AuthenticatedUser user = exigirUnidade(request, unidade);
        if (!podeArea(user, area)) {
            throw new ForbiddenException("Sem permissao para esta area da farmacia. Solicite a liberacao em Cadastro de Servidores.");
        }
        return user;
    }

    static boolean podeArea(AuthenticatedUser user, String area) {
        // kfarmhmsfx so abria o Atendimento a Paciente: vale como dispensacao.
        return temAcessoTotal(user) || user.hasPermission(area) || (DISPENSACAO.equals(area) && user.hasPermission("kfarmhmsfx"));
    }

    static boolean temAcessoTotal(AuthenticatedUser user) {
        return ACESSO_TOTAL.stream().anyMatch(user::hasPermission);
    }
}
