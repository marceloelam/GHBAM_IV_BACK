package br.com.iv.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AuthenticatedUser(
        String idServidor,
        String matricula,
        String nome,
        String cargo,
        String unidade,
        String cns,
        String crm,
        String corem,
        String cbo,
        String especialidade,
        String foto,
        String usuarioad,
        boolean senhaInicial,
        Map<String, Boolean> permissions) {

    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        if (!"ksuperusuario".equals(permission) && !"superusuario".equals(permission)
                && (Boolean.TRUE.equals(permissions.get("ksuperusuario"))
                        || Boolean.TRUE.equals(permissions.get("superusuario")))) {
            return true;
        }
        return Boolean.TRUE.equals(permissions.get(permission));
    }

    public boolean hasAnyPermission(Iterable<String> requiredPermissions) {
        if (requiredPermissions == null) {
            return true;
        }
        for (String permission : requiredPermissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    // Diferente de hasAnyPermission (OU): exige TODAS as permissoes da lista.
    // Usado para permissoes finas que devem valer como restricao de verdade
    // (ex.: pimprimirbam) em cima de uma rota cujo acesso basico ja e liberado
    // por permissao de setor mais ampla via hasAnyPermission. superusuario
    // continua bypassando, porque hasPermission ja da bypass por item.
    public boolean hasAllPermissions(Iterable<String> requiredPermissions) {
        if (requiredPermissions == null) {
            return true;
        }
        for (String permission : requiredPermissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("autenticado", true);
        body.put("idServidor", idServidor);
        body.put("matricula", matricula);
        body.put("nome", nome);
        body.put("cargo", cargo);
        body.put("unidade", unidade);
        body.put("cns", cns);
        body.put("crm", crm);
        body.put("corem", corem);
        body.put("cbo", cbo);
        body.put("especialidade", especialidade);
        body.put("foto", foto);
        body.put("usuarioad", usuarioad);
        body.put("senhaInicial", senhaInicial);
        body.put("permissoes", permissions);
        body.put("fonte", "backend-db");
        return body;
    }

    public static AuthenticatedUser empty() {
        return new AuthenticatedUser("", "", "", "", "", "", "", "", "", "", "", "", false, Collections.emptyMap());
    }

    // Usado na troca de unidade (sem novo login): mesma identidade/permissoes,
    // so a unidade da sessao muda.
    public AuthenticatedUser comUnidade(String novaUnidade) {
        return new AuthenticatedUser(idServidor, matricula, nome, cargo, novaUnidade, cns, crm, corem, cbo,
                especialidade, foto, usuarioad, senhaInicial, permissions);
    }
}
