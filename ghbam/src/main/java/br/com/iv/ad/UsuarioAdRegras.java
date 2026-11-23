package br.com.iv.ad;

import java.util.Locale;

/**
 * Valores especiais de servidor.usuarioad. A mesma regra existe no frontend em
 * services/usuarioAdRules.js.
 *
 * "-" era a antiga isencao de AD e agora impede o acesso ate o cadastro ser
 * regularizado. "semad" e a isencao atual e so superusuario ou administrativo (kadm) concede.
 * "admin" e a conta de recuperacao criada pelo proprio sistema.
 */
public final class UsuarioAdRegras {

    // Mensagem neutra de proposito: os valores especiais nao sao divulgados ao operador.
    private static final String MENSAGEM_RECUSA = "Usuário do AD inválido. Informe no formato nome.sobrenome.";

    private UsuarioAdRegras() {
    }

    private static String normalizar(String valor) {
        // Inclui os mesmos espacos que o \s e o trim do JavaScript (ex.: NBSP): se o
        // backend lesse menos que o frontend, uma variacao passaria como isencao no login.
        return valor == null ? "" : valor.replaceAll("[\\s\\p{Z}\\uFEFF]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    public static boolean bloqueado(String valor) {
        return "-".equals(normalizar(valor));
    }

    public static boolean isento(String valor) {
        String normalizado = normalizar(valor);
        return "semad".equals(normalizado) || "sem ad".equals(normalizado) || "admin".equals(normalizado);
    }

    /**
     * Valor que ja estava no cadastro pode ser reenviado sem mudanca: editar
     * outro campo nao exige regularizar o usuario do AD no mesmo ato.
     *
     * @param atual valor gravado, ou null em cadastro novo
     * @return mensagem de recusa, ou null quando a alteracao e permitida
     */
    public static String motivoRecusa(String novo, String atual, boolean superusuario) {
        if ((bloqueado(novo) && !bloqueado(atual)) || (isento(novo) && !isento(atual) && !superusuario)) {
            return MENSAGEM_RECUSA;
        }
        return null;
    }
}
