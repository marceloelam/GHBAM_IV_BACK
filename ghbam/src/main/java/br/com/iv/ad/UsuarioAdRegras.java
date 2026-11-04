package br.com.iv.ad;

import java.util.Locale;

/**
 * Valores especiais de servidor.usuarioad. A mesma regra existe no frontend em
 * services/usuarioAdRules.js.
 *
 * "-" era a antiga isencao de AD e agora impede o acesso ate o cadastro ser
 * regularizado. "semad" e a isencao atual e so superusuario pode conceder.
 * "admin" e a conta de recuperacao criada pelo proprio sistema.
 */
public final class UsuarioAdRegras {

    private UsuarioAdRegras() {
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean bloqueado(String valor) {
        return "-".equals(normalizar(valor));
    }

    public static boolean isento(String valor) {
        String normalizado = normalizar(valor);
        return "semad".equals(normalizado) || "admin".equals(normalizado);
    }

    /**
     * Valor que ja estava no cadastro pode ser reenviado sem mudanca: editar
     * outro campo nao exige regularizar o usuario do AD no mesmo ato.
     *
     * @param atual valor gravado, ou null em cadastro novo
     * @return mensagem de recusa, ou null quando a alteracao e permitida
     */
    public static String motivoRecusa(String novo, String atual, boolean superusuario) {
        if (bloqueado(novo) && !bloqueado(atual)) {
            return "O valor \"-\" nao e mais aceito no usuario do AD. Informe nome.sobrenome ou peca a um superusuario para marcar Sem AD.";
        }
        if (isento(novo) && !isento(atual) && !superusuario) {
            return "Somente superusuario pode dispensar a verificacao no AD.";
        }
        return null;
    }
}
