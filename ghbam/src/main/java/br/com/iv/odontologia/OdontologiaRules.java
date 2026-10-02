package br.com.iv.odontologia;

import java.util.Set;

public final class OdontologiaRules {
    private static final Set<String> FACES = Set.of(
            "DENTE_INTEIRO", "VESTIBULAR", "LINGUAL", "PALATINA",
            "MESIAL", "DISTAL", "OCLUSAL", "INCISAL", "RAIZ");
    private static final Set<String> CONDICOES = Set.of(
            "HIGIDO", "CARIE", "RESTAURACAO", "AUSENTE", "EXTRACAO_INDICADA",
            "FRATURA", "COROA", "IMPLANTE", "ENDODONTIA", "MOBILIDADE", "CALCULO", "OUTRO");

    private OdontologiaRules() {
    }

    public static boolean numeroFdiValido(int numero) {
        int quadrante = numero / 10;
        int posicao = numero % 10;
        return ((quadrante >= 1 && quadrante <= 4) && posicao >= 1 && posicao <= 8)
                || ((quadrante >= 5 && quadrante <= 8) && posicao >= 1 && posicao <= 5);
    }

    public static String numeroProntuario(int anoCriacao, int sequencial) {
        if (anoCriacao < 2000 || sequencial <= 0) {
            throw new IllegalArgumentException("Ano e sequencial do prontuario odontologico sao obrigatorios.");
        }
        return String.format("%04d-%06d", anoCriacao, sequencial);
    }

    public static boolean faceValida(String face) {
        return face != null && FACES.contains(face.toUpperCase());
    }

    public static boolean condicaoValida(String condicao) {
        return condicao != null && CONDICOES.contains(condicao.toUpperCase());
    }

    public static void exigirEditavel(String status) {
        if (!"RASCUNHO".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Atendimento odontologico assinado nao pode ser alterado; registre um adendo.");
        }
    }
}
