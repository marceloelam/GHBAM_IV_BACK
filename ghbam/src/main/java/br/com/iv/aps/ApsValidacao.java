package br.com.iv.aps;

import java.time.LocalDate;
import java.time.Period;

/**
 * Regras de validacao do LEDI APS (docs/aps/referencia-oficial/ledi-8.7.0/doc/regras_*).
 */
public final class ApsValidacao {

    private ApsValidacao() {
    }

    /** Algoritmo oficial de validacao do CNS (definitivo 1/2 e provisorio 7/8/9). */
    public static boolean cnsValido(String cns) {
        if (cns == null) {
            return false;
        }
        String c = cns.replaceAll("\\D", "");
        if (c.length() != 15) {
            return false;
        }
        char first = c.charAt(0);
        if (first == '1' || first == '2') {
            String pis = c.substring(0, 11);
            int soma = 0;
            for (int i = 0; i < 11; i++) {
                soma += Character.getNumericValue(pis.charAt(i)) * (15 - i);
            }
            int resto = soma % 11;
            int dv = 11 - resto;
            if (dv == 11) {
                dv = 0;
            }
            String resultado;
            if (dv == 10) {
                soma += 2;
                resto = soma % 11;
                dv = 11 - resto;
                resultado = pis + "001" + dv;
            } else {
                resultado = pis + "000" + dv;
            }
            return c.equals(resultado);
        }
        if (first == '7' || first == '8' || first == '9') {
            int soma = 0;
            for (int i = 0; i < 15; i++) {
                soma += Character.getNumericValue(c.charAt(i)) * (15 - i);
            }
            return soma % 11 == 0;
        }
        return false;
    }

    public static boolean cpfValido(String cpf) {
        if (cpf == null) {
            return false;
        }
        String c = cpf.replaceAll("\\D", "");
        if (c.length() != 11 || c.chars().distinct().count() == 1) {
            return false;
        }
        int d1 = digito(c, 9, 10);
        int d2 = digito(c, 10, 11);
        return d1 == Character.getNumericValue(c.charAt(9)) && d2 == Character.getNumericValue(c.charAt(10));
    }

    private static int digito(String c, int len, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < len; i++) {
            soma += Character.getNumericValue(c.charAt(i)) * (pesoInicial - i);
        }
        int r = (soma * 10) % 11;
        return r == 10 ? 0 : r;
    }

    /** CPF e a chave principal desde o LEDI 8.4; sem CPF exige-se CNS valido ou justificativa. */
    public static void validarIdentificacao(String cpf, String cns, Object justificativaSemCpf, String rotulo) {
        String c = cpf == null ? null : cpf.replaceAll("\\D", "");
        if (c != null && !c.isEmpty()) {
            if (!cpfValido(c)) {
                throw new IllegalArgumentException("CPF invalido para " + rotulo + ".");
            }
            return;
        }
        if (cns != null && !cns.isBlank() && !cnsValido(cns)) {
            throw new IllegalArgumentException("CNS invalido para " + rotulo + ".");
        }
        if (justificativaSemCpf == null && (cns == null || cns.isBlank())) {
            throw new IllegalArgumentException("Informe CPF, CNS ou a justificativa de 'cidadao nao possui CPF' para " + rotulo + ".");
        }
    }

    public static int idadeAnos(LocalDate nascimento, LocalDate referencia) {
        if (nascimento == null) {
            return -1;
        }
        return Period.between(nascimento, referencia == null ? LocalDate.now() : referencia).getYears();
    }

    public static int idadeMeses(LocalDate nascimento, LocalDate referencia) {
        if (nascimento == null) {
            return -1;
        }
        Period p = Period.between(nascimento, referencia == null ? LocalDate.now() : referencia);
        return p.getYears() * 12 + p.getMonths();
    }

    public static String digitos(String s) {
        return ApsContextoService.somenteDigitos(s);
    }
}
