package br.com.iv.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decisao humana sobre cada caso (suspeito, provavel, confirmado, descartado). Ela vale sobre a leitura
 * automatica do texto: descartado sai da contagem e confirmado entra, mesmo fora da definicao de caso.
 */
public final class ClassificacaoCasoEpidemiologico {

    private ClassificacaoCasoEpidemiologico() {}

    public static final String SUSPEITO = "SUSPEITO";
    public static final String PROVAVEL = "PROVAVEL";
    public static final String CONFIRMADO = "CONFIRMADO";
    public static final String DESCARTADO = "DESCARTADO";
    private static final Set<String> CLASSIFICACOES = Set.of(SUSPEITO, PROVAVEL, CONFIRMADO, DESCARTADO);
    private static final Set<String> CRITERIOS = Set.of("LABORATORIAL", "CLINICO_EPIDEMIOLOGICO", "CLINICO");

    /** Mensagem de erro, ou null quando a classificacao pode ser gravada. */
    public static String validar(String classificacao, String criterio, String justificativa) {
        if (!CLASSIFICACOES.contains(classificacao)) return "Classificação inválida.";
        if (justificativa == null || justificativa.isBlank()) return "Informe a justificativa da classificação.";
        if (CONFIRMADO.equals(classificacao) && !CRITERIOS.contains(criterio)) return "Informe o critério de confirmação.";
        if (criterio != null && !criterio.isBlank() && !CRITERIOS.contains(criterio)) return "Critério inválido.";
        return null;
    }

    /**
     * Ajusta as colunas 13 (e caso), 14 (cumpre a definicao) e 15 (cita termos) de cada linha da varredura.
     * Coluna 1 = paciente, 2 = atendimento (BAM). O G-PEC nao tem classificacao por caso.
     *
     * @param porRegra para cada regra, na ordem dos indices: atendimento -> classificacao mais recente
     * @return para cada regra, pacientes {confirmados, provaveis, descartados}; confirmado vale sobre os
     *         demais e so conta como descartado o paciente que nao tem outro atendimento contando como caso
     */
    public static long[][] aplicar(List<Object[]> linhas, List<Map<String, String>> porRegra) {
        int total = porRegra.size();
        List<Set<String>> confirmados = conjuntos(total);
        List<Set<String>> provaveis = conjuntos(total);
        List<Set<String>> descartados = conjuntos(total);
        List<Set<String>> casos = conjuntos(total);
        for (Object[] linha : linhas) {
            if (!"GHBAM".equals(linha[0])) continue;
            String paciente = String.valueOf(linha[1]);
            String atendimento = String.valueOf(linha[2]);
            Set<String> emCaso = indices(linha[13]);
            Set<String> cumprem = indices(linha[14]);
            Set<String> citam = indices(linha[15]);
            for (int i = 0; i < total; i++) {
                String indice = String.valueOf(i);
                String classificacao = porRegra.get(i).get(atendimento);
                if (DESCARTADO.equals(classificacao)) {
                    emCaso.remove(indice);
                    cumprem.remove(indice);
                    citam.remove(indice);
                    descartados.get(i).add(paciente);
                } else if (citam.contains(indice) && (CONFIRMADO.equals(classificacao) || PROVAVEL.equals(classificacao))) {
                    emCaso.add(indice);
                    cumprem.add(indice);
                    (CONFIRMADO.equals(classificacao) ? confirmados : provaveis).get(i).add(paciente);
                }
                if (emCaso.contains(indice)) casos.get(i).add(paciente);
            }
            linha[13] = String.join(",", emCaso);
            linha[14] = String.join(",", cumprem);
            linha[15] = String.join(",", citam);
        }
        long[][] resumo = new long[total][3];
        for (int i = 0; i < total; i++) {
            provaveis.get(i).removeAll(confirmados.get(i));
            descartados.get(i).removeAll(casos.get(i));
            resumo[i] = new long[] { confirmados.get(i).size(), provaveis.get(i).size(), descartados.get(i).size() };
        }
        return resumo;
    }

    /** Para mostrar um paciente com varios atendimentos: confirmado vale sobre provavel, e provavel sobre suspeito. */
    public static String maisForte(String atual, String nova) {
        if (CONFIRMADO.equals(atual) || CONFIRMADO.equals(nova)) return CONFIRMADO;
        if (PROVAVEL.equals(atual) || PROVAVEL.equals(nova)) return PROVAVEL;
        return SUSPEITO;
    }

    private static List<Set<String>> conjuntos(int total) {
        List<Set<String>> out = new ArrayList<>();
        for (int i = 0; i < total; i++) out.add(new HashSet<>());
        return out;
    }

    private static Set<String> indices(Object coluna) {
        Set<String> out = new LinkedHashSet<>();
        if (coluna == null) return out;
        Arrays.stream(String.valueOf(coluna).split(",")).filter(indice -> !indice.isBlank()).forEach(out::add);
        return out;
    }
}
