package br.com.iv.service;

// Validacao do Cartao Nacional de Saude (modulo 11), a mesma conta que o DATASUS faz
// ao processar a remessa. Antes disto o motor so checava se o campo estava preenchido:
// um numero digitado errado passava por APTO_FATURAMENTO, entrava no arquivo BPA e so
// voltava rejeitado depois do envio, com a competencia ja fechada.
//
// Duas familias, com regras diferentes:
//   1 e 2    - CNS definitivo, derivado do PIS/PASEP: os 11 primeiros digitos geram os
//              4 ultimos (o "000"/"001" faz parte do resultado, nao e enchimento).
//   7, 8 e 9 - CNS provisorio (o caso do recem-nascido cadastrado pela DNV): nao ha
//              parte derivada, o numero inteiro precisa fechar a soma ponderada.
// Qualquer outro digito inicial nao e CNS.
//
// A mesma regra existe no frontend (cnsSus.js) para barrar o erro na digitacao; aqui
// ela e a garantia, porque producao tambem entra por outros caminhos.
public final class CnsUtils {

    private static final int TAMANHO = 15;

    private CnsUtils() {
    }

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public static boolean valido(String valor) {
        String digitos = somenteDigitos(valor);
        if (digitos.length() != TAMANHO) {
            return false;
        }
        // Sequencia repetida fecha a conta em alguns casos, mas nunca e um CNS real -
        // e o valor tipico de campo preenchido "so para passar".
        if (digitos.chars().distinct().count() == 1) {
            return false;
        }
        char inicio = digitos.charAt(0);
        if (inicio == '1' || inicio == '2') {
            return definitivoValido(digitos);
        }
        if (inicio == '7' || inicio == '8' || inicio == '9') {
            return somaPonderada(digitos, TAMANHO) % 11 == 0;
        }
        return false;
    }

    // Motivo em texto para a mensagem de erro: diz o que esta errado, nao so que esta.
    public static String motivoInvalido(String valor) {
        String digitos = somenteDigitos(valor);
        if (digitos.isEmpty()) {
            return "CNS nao informado.";
        }
        if (digitos.length() != TAMANHO) {
            return "CNS tem 15 digitos e foram informados " + digitos.length() + ".";
        }
        if (digitos.chars().distinct().count() == 1) {
            return "Numero com todos os digitos iguais nao e um CNS valido.";
        }
        char inicio = digitos.charAt(0);
        if (inicio != '1' && inicio != '2' && inicio != '7' && inicio != '8' && inicio != '9') {
            return "CNS comeca com 1, 2 (definitivo) ou 7, 8, 9 (provisorio).";
        }
        return "Digito verificador do CNS nao confere - confira o numero no CADSUS.";
    }

    private static boolean definitivoValido(String digitos) {
        String base = digitos.substring(0, 11);
        long soma = somaPonderada(digitos, 11);
        long resto = soma % 11;
        long dv = 11 - resto;
        if (dv == 11) {
            dv = 0;
        }
        // dv 10 nao cabe em uma casa: a regra oficial soma 2 e recalcula, e o "001" no
        // resultado e o que marca esse recalculo.
        if (dv == 10) {
            soma += 2;
            resto = soma % 11;
            dv = 11 - resto;
            return digitos.equals(base + "001" + dv);
        }
        return digitos.equals(base + "000" + dv);
    }

    private static long somaPonderada(String digitos, int ate) {
        long soma = 0;
        for (int i = 0; i < ate; i++) {
            soma += Character.getNumericValue(digitos.charAt(i)) * (15L - i);
        }
        return soma;
    }
}
