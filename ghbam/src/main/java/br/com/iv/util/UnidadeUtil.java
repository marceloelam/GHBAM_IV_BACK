package br.com.iv.util;

import java.util.Locale;

public final class UnidadeUtil {

    private UnidadeUtil() {
    }

    public static String normalizar(String unidade) {
        if (unidade == null) {
            return "";
        }
        return unidade.split(":", 2)[0].trim().toUpperCase(Locale.ROOT);
    }
}
