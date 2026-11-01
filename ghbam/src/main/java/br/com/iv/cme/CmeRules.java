package br.com.iv.cme;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * Regras da rastreabilidade da CME (RDC 15/2012), sem dependencia de banco
 * para poderem ser testadas isoladamente.
 */
public final class CmeRules {

    public static final String CICLO_EM_ANALISE = "EM_ANALISE";
    public static final String CICLO_LIBERADO = "LIBERADO";
    public static final String CICLO_REPROVADO = "REPROVADO";
    /** Aberto por engano e nunca liberado; fica no historico, nao e apagado. */
    public static final String CICLO_CANCELADO = "CANCELADO";

    public static final String PACOTE_ESTERILIZADO = "ESTERILIZADO";
    public static final String PACOTE_DISTRIBUIDO = "DISTRIBUIDO";
    public static final String PACOTE_UTILIZADO = "UTILIZADO";
    public static final String PACOTE_RECOLHIDO = "RECOLHIDO";
    public static final String PACOTE_BAIXADO = "BAIXADO";

    public static final String CONFORME = "CONFORME";
    public static final String NAO_CONFORME = "NAO_CONFORME";
    public static final String PENDENTE = "PENDENTE";

    public static final String IB_NAO_REALIZADO = "NAO_REALIZADO";
    public static final String IB_NEGATIVO = "NEGATIVO";
    public static final String IB_POSITIVO = "POSITIVO";

    public static final Set<String> METODOS = Set.of(
            "VAPOR", "PEROXIDO_HIDROGENIO", "OXIDO_ETILENO", "FORMALDEIDO", "OUTRO");

    private static final Set<String> INDICADORES = Set.of(PENDENTE, CONFORME, NAO_CONFORME);
    private static final Set<String> BIOLOGICOS = Set.of(IB_NAO_REALIZADO, PENDENTE, IB_NEGATIVO, IB_POSITIVO);

    private CmeRules() {
    }

    public static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    public static String metodo(String valor) {
        String m = normalizar(valor);
        if (!METODOS.contains(m)) {
            throw new IllegalArgumentException("Metodo de esterilizacao invalido.");
        }
        return m;
    }

    public static String indicador(String valor) {
        String i = normalizar(valor);
        if (i.isEmpty()) {
            return PENDENTE;
        }
        if (!INDICADORES.contains(i)) {
            throw new IllegalArgumentException("Resultado de indicador invalido: " + valor);
        }
        return i;
    }

    public static String biologico(String valor) {
        String b = normalizar(valor);
        if (b.isEmpty()) {
            return IB_NAO_REALIZADO;
        }
        if (!BIOLOGICOS.contains(b)) {
            throw new IllegalArgumentException("Resultado de indicador biologico invalido: " + valor);
        }
        return b;
    }

    /** Falha em qualquer indicador reprova o ciclo e obriga recolhimento dos pacotes. */
    public static boolean exigeReprovacao(String fisico, String quimico, String biologico) {
        return NAO_CONFORME.equals(normalizar(fisico))
                || NAO_CONFORME.equals(normalizar(quimico))
                || IB_POSITIVO.equals(normalizar(biologico));
    }

    /**
     * Motivo que impede a liberacao do ciclo, ou null se pode liberar.
     * Indicador fisico registrado a cada ciclo (art. 97) e quimico conforme sao
     * obrigatorios. Carga com implante so e liberada com biologico negativo.
     */
    public static String bloqueioLiberacao(String statusCiclo, String fisico, String quimico, String biologico,
            boolean temImplante, int totalPacotes) {
        if (!CICLO_EM_ANALISE.equals(normalizar(statusCiclo))) {
            return "Somente ciclo em analise pode ser liberado.";
        }
        if (totalPacotes <= 0) {
            return "Inclua ao menos um pacote antes de liberar o ciclo.";
        }
        if (exigeReprovacao(fisico, quimico, biologico)) {
            return "Indicador nao conforme: o ciclo deve ser reprovado, nao liberado.";
        }
        if (!CONFORME.equals(normalizar(fisico))) {
            return "Registre o indicador fisico (parametros do ciclo) antes de liberar.";
        }
        if (!CONFORME.equals(normalizar(quimico))) {
            return "Registre o indicador quimico antes de liberar.";
        }
        if (temImplante && !IB_NEGATIVO.equals(normalizar(biologico))) {
            return "Carga com implante so pode ser liberada com indicador biologico negativo.";
        }
        return null;
    }

    public static boolean vencido(LocalDate validade, LocalDate hoje) {
        return validade == null || validade.isBefore(hoje);
    }

    /** Motivo que impede distribuir ou usar o pacote, ou null se pode. */
    public static String bloqueioSaida(String statusCiclo, String statusPacote, LocalDate validade, LocalDate hoje,
            boolean paraUso) {
        if (!CICLO_LIBERADO.equals(normalizar(statusCiclo))) {
            if (CICLO_REPROVADO.equals(normalizar(statusCiclo))) {
                return "Ciclo reprovado: pacote deve ser recolhido e reprocessado.";
            }
            return CICLO_CANCELADO.equals(normalizar(statusCiclo))
                    ? "Ciclo cancelado: pacote sem validade de esterilizacao."
                    : "Ciclo ainda nao liberado pela CME.";
        }
        String s = normalizar(statusPacote);
        boolean permitido = PACOTE_ESTERILIZADO.equals(s) || (paraUso && PACOTE_DISTRIBUIDO.equals(s));
        if (!permitido) {
            return "Pacote em situacao " + s + " nao pode ser " + (paraUso ? "utilizado." : "distribuido.");
        }
        if (vencido(validade, hoje)) {
            return "Pacote com validade vencida: encaminhe para reprocessamento.";
        }
        return null;
    }

    /** Pacote que ainda nao chegou ao paciente e deve ser recolhido quando o ciclo e reprovado. */
    public static boolean recolhivel(String statusPacote) {
        String s = normalizar(statusPacote);
        return PACOTE_ESTERILIZADO.equals(s) || PACOTE_DISTRIBUIDO.equals(s);
    }

    /** Motivo que impede cancelar, ou null. Liberado ou reprovado ja produziu efeito e nao se cancela. */
    public static String bloqueioCancelamento(String statusCiclo) {
        String s = normalizar(statusCiclo);
        if (CICLO_EM_ANALISE.equals(s)) {
            return null;
        }
        return CICLO_REPROVADO.equals(s)
                ? "Ciclo reprovado nao pode ser cancelado: reprocesse os pacotes recolhidos."
                : "Somente ciclo em analise (nunca liberado) pode ser cancelado.";
    }

    public static boolean podeBaixar(String statusPacote) {
        String s = normalizar(statusPacote);
        return !PACOTE_UTILIZADO.equals(s) && !PACOTE_BAIXADO.equals(s);
    }

    /** Recolhido (ciclo reprovado) ou vencido ainda no arsenal volta para um novo ciclo. */
    public static boolean reprocessavel(String statusPacote, LocalDate validade, LocalDate hoje) {
        String s = normalizar(statusPacote);
        return PACOTE_RECOLHIDO.equals(s) || (recolhivel(s) && vencido(validade, hoje));
    }

    // Preparo (antes da esterilizacao): RECEBIDO -> LIMPO -> PRONTO -> NA_CARGA.
    public static final String PREPARO_RECEBIDO = "RECEBIDO";
    public static final String PREPARO_LIMPO = "LIMPO";
    public static final String PREPARO_PRONTO = "PRONTO";
    public static final String PREPARO_NA_CARGA = "NA_CARGA";
    public static final String PREPARO_DESCARTADO = "DESCARTADO";

    public static final Set<String> METODOS_LIMPEZA = Set.of("MANUAL", "TERMODESINFECCAO", "ULTRASSONICA");
    public static final Set<String> EMBALAGENS = Set.of("GRAU_CIRURGICO", "SMS", "TECIDO", "CONTAINER", "OUTRO");

    public static String metodoLimpeza(String valor) {
        String m = normalizar(valor);
        if (!METODOS_LIMPEZA.contains(m)) {
            throw new IllegalArgumentException("Metodo de limpeza invalido.");
        }
        return m;
    }

    public static String embalagem(String valor) {
        String e = normalizar(valor);
        if (!EMBALAGENS.contains(e)) {
            throw new IllegalArgumentException("Tipo de embalagem invalido.");
        }
        return e;
    }

    /** Status exigido para cada etapa do preparo (limpar exige RECEBIDO; preparar exige LIMPO). */
    public static String bloqueioEtapaPreparo(String statusAtual, String etapa) {
        String s = normalizar(statusAtual);
        String exigido = switch (normalizar(etapa)) {
            case "LIMPAR" -> PREPARO_RECEBIDO;
            case "PREPARAR" -> PREPARO_LIMPO;
            case "CARGA" -> PREPARO_PRONTO;
            default -> throw new IllegalArgumentException("Etapa invalida.");
        };
        return exigido.equals(s) ? null : "Caixa em " + s + ": esta etapa exige caixa " + exigido + ".";
    }

    /** Proximo numero sequencial do equipamento; numeros digitados fora do padrao numerico sao ignorados. */
    public static String proximoNumeroCiclo(Iterable<String> existentes) {
        long maior = 0;
        if (existentes != null) {
            for (String n : existentes) {
                String s = normalizar(n);
                if (!s.isEmpty() && s.length() <= 18 && s.chars().allMatch(Character::isDigit)) {
                    maior = Math.max(maior, Long.parseLong(s));
                }
            }
        }
        return String.valueOf(maior + 1);
    }

    public static String codigoPacote(String unidade, long idpacote) {
        return String.format(Locale.ROOT, "CME-%s-%07d", normalizar(unidade), idpacote);
    }
}
