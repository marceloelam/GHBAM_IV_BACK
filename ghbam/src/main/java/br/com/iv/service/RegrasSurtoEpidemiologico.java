package br.com.iv.service;

import java.util.Map;
import java.util.Set;

/**
 * Quem declara surto e uma pessoa, nunca o sistema. Toda investigacao comeca em EM_INVESTIGACAO e so vira
 * CONFIRMADO com casos confirmados vinculados: dois pacientes, ou um em agravo raro ou grave. Minimos a validar
 * com a vigilancia. DESCARTADO e ENCERRADO sao finais: reabrir seria outra investigacao, com novo historico.
 */
public final class RegrasSurtoEpidemiologico {

    private RegrasSurtoEpidemiologico() {}

    public static final String EM_INVESTIGACAO = "EM_INVESTIGACAO";
    public static final String CONFIRMADO = "CONFIRMADO";
    public static final String DESCARTADO = "DESCARTADO";
    public static final String ENCERRADO = "ENCERRADO";

    private static final Map<String, Set<String>> PERMITIDAS = Map.of(
            EM_INVESTIGACAO, Set.of(CONFIRMADO, DESCARTADO),
            CONFIRMADO, Set.of(ENCERRADO),
            DESCARTADO, Set.of(),
            ENCERRADO, Set.of());

    public static int minimoConfirmados(boolean casoUnico) {
        return casoUnico ? 1 : 2;
    }

    /** Mensagem de erro, ou null quando a mudanca pode ser gravada. */
    public static String validarMudanca(String atual, String novo, String justificativa, long pacientesConfirmados, boolean casoUnico) {
        if (!PERMITIDAS.getOrDefault(atual, Set.of()).contains(novo)) return "Esta mudança de situação não é permitida.";
        if (justificativa == null || justificativa.isBlank()) return "Informe a justificativa.";
        int minimo = minimoConfirmados(casoUnico);
        if (CONFIRMADO.equals(novo) && pacientesConfirmados < minimo) {
            return "Para declarar o surto, vincule ao menos " + minimo + (minimo == 1 ? " paciente confirmado." : " pacientes confirmados.");
        }
        return null;
    }

    /** Caso descartado nesta regra nao pode sustentar o surto. */
    public static String validarVinculo(String classificacao, String statusSurto) {
        if (!EM_INVESTIGACAO.equals(statusSurto) && !CONFIRMADO.equals(statusSurto)) return "Esta investigação já foi concluída.";
        if (ClassificacaoCasoEpidemiologico.DESCARTADO.equals(classificacao)) return "Caso descartado não pode ser vinculado.";
        return null;
    }
}
