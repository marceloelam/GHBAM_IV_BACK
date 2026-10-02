package br.com.iv.security;

import java.util.List;
import java.util.Map;

public record AuditUserContext(
        String idServidor,
        String matricula,
        String nome,
        String cargo,
        String unidade,
        String requestId,
        String userAgent) {

    public static AuditUserContext fromHeaders(Map<String, List<String>> headers, String userAgent) {
        return new AuditUserContext(
                first(headers, "X-GHBAM-USER-ID"),
                first(headers, "X-GHBAM-MATRICULA"),
                first(headers, "X-GHBAM-USER-NAME"),
                first(headers, "X-GHBAM-ROLE"),
                first(headers, "X-GHBAM-UNIT"),
                first(headers, "X-GHBAM-REQUEST-ID"),
                safe(userAgent));
    }

    public static AuditUserContext fromAuthenticatedUser(AuthenticatedUser user, String requestId, String userAgent) {
        if (user == null) {
            return new AuditUserContext("", "", "", "", "", safe(requestId), safe(userAgent));
        }
        return new AuditUserContext(
                safe(user.idServidor()),
                safe(user.matricula()),
                safe(user.nome()),
                safe(user.cargo()),
                safe(user.unidade()),
                safe(requestId),
                safe(userAgent));
    }

    public boolean hasUser() {
        return !nome.isBlank() || !matricula.isBlank() || !idServidor.isBlank();
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            values = headers.get(name.toLowerCase());
        }
        return values == null || values.isEmpty() ? "" : safe(values.get(0));
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
