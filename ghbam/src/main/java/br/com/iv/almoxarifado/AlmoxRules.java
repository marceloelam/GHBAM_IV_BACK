package br.com.iv.almoxarifado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Regras do almoxarifado sem dependencia de banco, para teste isolado. */
public final class AlmoxRules {

    public static final String REQ_ABERTA = "ABERTA";
    public static final String REQ_ATENDIDA = "ATENDIDA";
    public static final String REQ_PARCIAL = "PARCIAL";
    public static final String REQ_RECUSADA = "RECUSADA";
    public static final String REQ_CANCELADA = "CANCELADA";

    public static final String MOV_ENTRADA = "ENTRADA";
    public static final String MOV_SAIDA = "SAIDA";
    public static final String MOV_AJUSTE = "AJUSTE";

    /** Sugestao inicial de setores que pedem material; a gestao inclui ou remove depois. */
    public static final List<String> SETORES_COMUNS = List.of(
            "ADMINISTRAÇÃO", "ALMOXARIFADO", "CENTRO CIRÚRGICO", "CME", "DIREÇÃO", "EMERGÊNCIA", "ENFERMARIA",
            "FARMÁCIA", "FATURAMENTO", "HIGIENIZAÇÃO", "LABORATÓRIO", "MANUTENÇÃO", "MATERNIDADE", "NIR",
            "NUTRIÇÃO", "PEDIATRIA", "RAIO X", "RECEPÇÃO", "RH", "ROUPARIA", "TI", "TRIAGEM", "UTI");

    public static final Set<String> GRUPOS = Set.of("CONSUMO", "EXPEDIENTE", "LIMPEZA", "ROUPARIA", "MANUTENCAO", "NUTRICAO", "OUTRO");

    private AlmoxRules() {
    }

    public static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    public static String grupo(String valor) {
        String g = normalizar(valor);
        if (g.isEmpty()) return "CONSUMO";
        if (!GRUPOS.contains(g)) throw new IllegalArgumentException("Grupo invalido.");
        return g;
    }

    /** Custo medio ponderado apos uma entrada. */
    public static BigDecimal custoMedio(BigDecimal saldo, BigDecimal custoAtual, BigDecimal qtdEntrada, BigDecimal custoEntrada) {
        BigDecimal s = saldo == null || saldo.signum() < 0 ? BigDecimal.ZERO : saldo;
        BigDecimal c = custoAtual == null ? BigDecimal.ZERO : custoAtual;
        BigDecimal ce = custoEntrada == null ? c : custoEntrada;
        BigDecimal total = s.add(qtdEntrada);
        if (total.signum() <= 0) return ce.setScale(4, RoundingMode.HALF_UP);
        return s.multiply(c).add(qtdEntrada.multiply(ce)).divide(total, 4, RoundingMode.HALF_UP);
    }

    public static boolean abaixoDoMinimo(BigDecimal saldo, BigDecimal minimo) {
        return minimo != null && minimo.signum() > 0 && saldo != null && saldo.compareTo(minimo) <= 0;
    }

    /** Situacao do pedido depois do atendimento, pelo que foi entregue de cada item. */
    public static String statusAtendimento(List<BigDecimal[]> solicitadoAtendido) {
        boolean tudo = true;
        boolean algum = false;
        for (BigDecimal[] par : solicitadoAtendido) {
            BigDecimal atendido = par[1] == null ? BigDecimal.ZERO : par[1];
            if (atendido.signum() > 0) algum = true;
            if (atendido.compareTo(par[0]) < 0) tudo = false;
        }
        if (tudo && algum) return REQ_ATENDIDA;
        return algum ? REQ_PARCIAL : REQ_RECUSADA;
    }

    // ---- Compras ----
    public static final String COMPRA_ABERTO = "ABERTO";
    public static final String COMPRA_ENVIADO = "ENVIADO";
    public static final String COMPRA_RECEBIDO = "RECEBIDO";
    public static final String COMPRA_PARCIAL = "PARCIAL";
    public static final String COMPRA_CANCELADO = "CANCELADO";
    /** Recebido em parte e encerrado: o restante nao vem mais. */
    public static final String COMPRA_ENCERRADO = "ENCERRADO";

    /** Pedido de compra que ainda aceita recebimento. */
    public static boolean compraRecebivel(String status) {
        return COMPRA_ABERTO.equals(status) || COMPRA_ENVIADO.equals(status) || COMPRA_PARCIAL.equals(status);
    }

    /** CNPJ sem mascara, em maiusculas (numerico ou alfanumerico). */
    public static String cnpjLimpo(String valor) {
        return valor == null ? "" : valor.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * CNPJ com digitos verificadores corretos (aceita mascara). Vale tambem o
     * alfanumerico: letra conta pelo codigo ASCII menos 48; os dois DVs sao numericos.
     */
    public static boolean cnpjValido(String valor) {
        String d = cnpjLimpo(valor);
        if (!d.matches("[0-9A-Z]{12}[0-9]{2}") || d.chars().distinct().count() == 1) return false;
        int[] pesos = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        for (int tamanho = 12; tamanho <= 13; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) {
                soma += (d.charAt(i) - '0') * pesos[pesos.length - tamanho + i];
            }
            int resto = soma % 11;
            int dv = resto < 2 ? 0 : 11 - resto;
            if (dv != d.charAt(tamanho) - '0') return false;
        }
        return true;
    }

    /** Quanto comprar para voltar ao maximo (ou ao dobro do minimo, se nao houver maximo). */
    public static BigDecimal quantidadeSugerida(BigDecimal saldo, BigDecimal minimo, BigDecimal maximo) {
        BigDecimal alvo = maximo != null && maximo.signum() > 0 ? maximo
                : (minimo == null ? BigDecimal.ZERO : minimo.multiply(BigDecimal.valueOf(2)));
        BigDecimal falta = alvo.subtract(saldo == null ? BigDecimal.ZERO : saldo);
        return falta.signum() > 0 ? falta : BigDecimal.ZERO;
    }

    // ---- Patrimonio ----
    public static final String BEM_EM_USO = "EM_USO";
    public static final String BEM_MANUTENCAO = "MANUTENCAO";
    public static final String BEM_BAIXADO = "BAIXADO";
    public static final Set<String> CATEGORIAS_BEM = Set.of(
            "EQUIPAMENTO_MEDICO", "MOBILIARIO", "INFORMATICA", "ELETRODOMESTICO", "VEICULO", "OUTRO");

    public static String categoriaBem(String valor) {
        String c = normalizar(valor);
        if (c.isEmpty()) return "OUTRO";
        if (!CATEGORIAS_BEM.contains(c)) throw new IllegalArgumentException("Categoria de bem invalida.");
        return c;
    }

    /** Motivo que impede a acao no bem, ou null. Baixa e definitiva. */
    public static String bloqueioBem(String status, String acao) {
        String s = normalizar(status);
        String a = normalizar(acao);
        if (!Set.of("TRANSFERIR", "MANUTENCAO", "RETORNO", "BAIXA").contains(a)) throw new IllegalArgumentException("Acao invalida.");
        if (BEM_BAIXADO.equals(s)) return "Bem baixado nao aceita mais movimentacao.";
        return switch (a) {
            case "TRANSFERIR", "MANUTENCAO" -> BEM_EM_USO.equals(s) ? null : "O bem precisa estar em uso.";
            case "RETORNO" -> BEM_MANUTENCAO.equals(s) ? null : "O bem nao esta em manutencao.";
            case "BAIXA" -> null;
            default -> throw new IllegalArgumentException("Acao invalida.");
        };
    }

    /**
     * Curva ABC pelo valor consumido: A ate 80% do valor acumulado, B ate 95%, C o resto.
     * Recebe iditem -> valor e devolve iditem -> classe, do mais caro para o mais barato.
     */
    public static Map<Long, String> curvaAbc(Map<Long, BigDecimal> valorPorItem) {
        List<Map.Entry<Long, BigDecimal>> ordem = new ArrayList<>(valorPorItem.entrySet());
        ordem.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        BigDecimal total = ordem.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, String> classes = new LinkedHashMap<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : ordem) {
            if (total.signum() <= 0 || e.getValue().signum() <= 0) {
                classes.put(e.getKey(), "C");
                continue;
            }
            // Classe pela fatia acumulada ANTES do item: o item que cruza 80% ainda e A.
            BigDecimal antes = acumulado.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP);
            classes.put(e.getKey(), antes.compareTo(BigDecimal.valueOf(80)) < 0 ? "A"
                    : antes.compareTo(BigDecimal.valueOf(95)) < 0 ? "B" : "C");
            acumulado = acumulado.add(e.getValue());
        }
        return classes;
    }
}
