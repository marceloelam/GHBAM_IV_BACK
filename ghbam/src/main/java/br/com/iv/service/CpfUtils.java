package br.com.iv.service;

/**
 * Validacao de CPF (modulo 11), o par do CnsUtils.
 *
 * Existe por causa do lote de busca de CNS: a consulta ao CADSUS e feita por CPF, e ate
 * aqui o unico criterio era "tem 11 digitos". Numero digitado errado passava, gastava uma
 * consulta ao servidor do DATASUS (tres requisicoes e ate 15s) e voltava como "paciente
 * nao localizado" - o que faz o operador procurar o erro no CADSUS em vez de no cadastro
 * local, que e onde ele esta.
 *
 * Mesma regra da tela (cpfSus.js no frontend), para os dois lados dizerem a mesma coisa
 * sobre o mesmo numero.
 */
public final class CpfUtils {

    private CpfUtils() {
    }

    public static boolean valido(String bruto) {
        String digitos = bruto == null ? "" : bruto.replaceAll("[^0-9]", "");
        if (digitos.length() != 11) {
            return false;
        }
        // Sequencia repetida fecha os dois digitos verificadores (111.111.111-11), mas nao
        // e CPF de ninguem - e o valor tipico de campo preenchido so para passar.
        if (digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(digitos, 9, 10) == digitos.charAt(9) - '0'
                && digitoVerificador(digitos, 10, 11) == digitos.charAt(10) - '0';
    }

    private static int digitoVerificador(String digitos, int tamanhoBase, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanhoBase; i++) {
            soma += (digitos.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
