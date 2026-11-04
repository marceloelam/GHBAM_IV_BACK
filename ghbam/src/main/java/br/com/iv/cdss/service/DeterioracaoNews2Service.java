package br.com.iv.cdss.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * NEWS2 automatico a partir dos sinais vitais ja registrados pela enfermagem
 * (prescricaocont), sem digitacao extra. Calcula a afericao mais recente e a
 * anterior de cada BAM para mostrar tendencia de piora.
 *
 * O registro atual nao tem O2 suplementar nem nivel de consciencia: entram como
 * ar ambiente e alerta, e a resposta avisa que o escore pode estar subestimado.
 * Valor zerado ou fora da faixa fisiologica conta como nao aferido (erro de digitacao
 * nao pode gerar nem esconder alerta). Apoio a decisao: nao substitui avaliacao clinica.
 */
@ApplicationScoped
public class DeterioracaoNews2Service {
    static final int MINIMO_PARAMETROS = 3;
    static final int HORAS_AFERICAO_ATRASADA = 6;
    static final String AVISO_PARCIAL = "O2 suplementar e nível de consciência não foram registrados nesta aferição: "
            + "considerados ar ambiente e alerta. Se o paciente usa O2 ou está confuso/sonolento, o risco real é maior.";

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter BR_CURTO = DateTimeFormatter.ofPattern("dd/MM/yy");

    // Sem estado e sem dependencias: instancia propria para reaproveitar as faixas do NEWS2.
    private final CdssRulesEngine regras = new CdssRulesEngine();

    @Inject
    EntityManager em;

    record Afericao(int id, String data, String hora, Double fr, Double spo, Double pas, Double fc, Double tax,
            Boolean o2, String consciencia) {
        Afericao(int id, String data, String hora, Double fr, Double spo, Double pas, Double fc, Double tax) {
            this(id, data, hora, fr, spo, pas, fc, tax, null, null);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, Map<String, Object>> porBam(List<Integer> bams) {
        Map<Integer, Map<String, Object>> resultado = new HashMap<>();
        if (bams == null || bams.isEmpty()) {
            return resultado;
        }
        List<Object[]> linhas = em.createNativeQuery(
                "select bam, idprescricaocontinuacao, data, hora, fr, spo, pasist, fc, tax, o2suplementar, consciencia from ("
                        + " select pc.*, row_number() over (partition by pc.bam order by pc.idprescricaocontinuacao desc) rn"
                        + " from prescricaocont pc"
                        + " where pc.bam in (:bams) and (pc.fr > 0 or pc.spo > 0 or pc.pasist > 0 or pc.fc > 0 or pc.tax > 0)"
                        + ") x where rn <= 2 order by bam, idprescricaocontinuacao desc")
                .setParameter("bams", bams)
                .getResultList();

        Map<Integer, List<Afericao>> porBam = new HashMap<>();
        for (Object[] l : linhas) {
            porBam.computeIfAbsent(((Number) l[0]).intValue(), k -> new ArrayList<>()).add(new Afericao(
                    ((Number) l[1]).intValue(), texto(l[2]), texto(l[3]),
                    numero(l[4]), numero(l[5]), numero(l[6]), numero(l[7]), numero(l[8]),
                    l[9] instanceof Boolean b ? b : null, texto(l[10]).isEmpty() ? null : texto(l[10])));
        }
        LocalDateTime agora = LocalDateTime.now();
        porBam.forEach((bam, lista) -> {
            Map<String, Object> atual = calcular(lista.get(0), agora);
            if (lista.size() > 1) {
                Map<String, Object> anterior = calcular(lista.get(1), agora);
                Map<String, Object> resumoAnterior = new LinkedHashMap<>();
                resumoAnterior.put("total", anterior.get("total"));
                resumoAnterior.put("aferidoEm", anterior.get("aferidoEm"));
                atual.put("anterior", resumoAnterior);
                atual.put("tendencia", tendencia(atual.get("total"), anterior.get("total")));
            }
            resultado.put(bam, atual);
        });
        return resultado;
    }

    /** Internados sem alta da unidade (sempre a da sessao, nunca a do cliente). */
    @SuppressWarnings("unchecked")
    public Map<Integer, Map<String, Object>> internadosDaUnidade(String unidade) {
        if (unidade == null || unidade.isBlank()) {
            throw new SecurityException("Sessao sem unidade identificada.");
        }
        List<Integer> bams = ((List<Object>) em.createNativeQuery(
                "select distinct n.nbam from novointernado n where n.unidade = :unidade and coalesce(n.alta, false) = false")
                .setParameter("unidade", unidade.trim())
                .getResultList()).stream().map(o -> ((Number) o).intValue()).toList();
        return porBam(bams);
    }

    /**
     * Peso mais recente do BAM para a calculadora de dose: ultima afericao com peso dos sinais
     * vitais (prescricaocont.peso2; peso la e HGT) e, sem ela, o peso da triagem. Faixa 0,3-350 kg.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pesoDoBam(int idbam) {
        if (idbam <= 0) return null;
        List<Object[]> sv = em.createNativeQuery("select peso2, data, hora from prescricaocont"
                + " where bam = :bam and peso2 > 0.3 and peso2 < 350 order by idprescricaocontinuacao desc")
                .setParameter("bam", idbam).setMaxResults(1).getResultList();
        if (!sv.isEmpty()) {
            Object[] l = sv.get(0);
            return Map.of("pesoKg", numero(l[0]), "fonte", "SINAIS_VITAIS", "aferidoEm", (texto(l[1]) + " " + texto(l[2])).trim());
        }
        List<Object[]> tr = em.createNativeQuery("select a.peso, a.data, a.hora from bam b"
                + " join avaliacao a on a.idavaliacao = b.avaliacao_idavaliacao"
                + " where b.idbam = :bam and a.peso > 0.3 and a.peso < 350")
                .setParameter("bam", idbam).getResultList();
        if (tr.isEmpty()) return null;
        Object[] l = tr.get(0);
        return Map.of("pesoKg", numero(l[0]), "fonte", "TRIAGEM", "aferidoEm", (texto(l[1]) + " " + texto(l[2])).trim());
    }

    /**
     * NEWS2/qSOFA de um BAM para as telas de atendimento (medico, multidisciplinar, odontologia).
     * Usa os sinais vitais da enfermagem; sem nenhum, cai para os da triagem (avaliacao do BAM),
     * com Glasgow < 15 como alteracao de consciencia. Sem dado valido devolve null.
     */
    public Map<String, Object> doBam(int idbam) {
        if (idbam <= 0) return null;
        return doBams(List.of(idbam)).get(idbam);
    }

    /** doBam em lote (quadro de fluxo de pacientes): uma consulta de sinais vitais e uma da triagem. */
    @SuppressWarnings("unchecked")
    public Map<Integer, Map<String, Object>> doBams(List<Integer> bams) {
        Map<Integer, Map<String, Object>> r = porBam(bams);
        r.values().forEach(m -> m.put("fonte", "SINAIS_VITAIS"));
        List<Integer> semSinais = bams == null ? List.of() : bams.stream().filter(b -> !r.containsKey(b)).toList();
        if (semSinais.isEmpty()) return r;
        List<Object[]> linhas = em.createNativeQuery(
                "select b.idbam, a.idavaliacao, a.data, a.hora, a.fr, a.spo, a.pasist, a.fc, a.tax, a.glasgow"
                        + " from bam b join avaliacao a on a.idavaliacao = b.avaliacao_idavaliacao where b.idbam in (:bams)")
                .setParameter("bams", semSinais)
                .getResultList();
        LocalDateTime agora = LocalDateTime.now();
        for (Object[] l : linhas) {
            Integer glasgow = l[9] instanceof Number n ? n.intValue() : null;
            String consciencia = glasgow == null || glasgow < 3 || glasgow > 15 ? null : glasgow == 15 ? "A" : "C";
            Map<String, Object> t = calcular(new Afericao(((Number) l[1]).intValue(), texto(l[2]), texto(l[3]),
                    numero(l[4]), numero(l[5]), numero(l[6]), numero(l[7]), numero(l[8]), null, consciencia), agora);
            if ("INSUFICIENTE".equals(t.get("risco"))) continue;
            t.put("fonte", "TRIAGEM");
            r.put(((Number) l[0]).intValue(), t);
        }
        return r;
    }

    Map<String, Object> calcular(Afericao a, LocalDateTime agora) {
        Map<String, Integer> componentes = new LinkedHashMap<>();
        List<String> faltando = new ArrayList<>();
        Map<String, Object> valores = new LinkedHashMap<>();

        Double fr = faixa(a.fr(), 1, 80);
        Double spo = faixa(a.spo(), 30, 100);
        Double tax = faixa(a.tax(), 30, 45);
        Double pas = faixa(a.pas(), 40, 300);
        Double fc = faixa(a.fc(), 20, 250);

        if (fr != null) { componentes.put("frequenciaRespiratoria", regras.scoreRespiratorio(fr.intValue())); valores.put("fr", fr.intValue()); } else faltando.add("FR");
        if (spo != null) { componentes.put("saturacaoOxigenio", regras.scoreSaturacao(spo.intValue())); valores.put("spo2", spo.intValue()); } else faltando.add("SpO2");
        if (tax != null) { componentes.put("temperatura", regras.scoreTemperatura(tax)); valores.put("temperatura", tax); } else faltando.add("Temperatura");
        if (pas != null) { componentes.put("pressaoSistolica", regras.scorePressao(pas.intValue())); valores.put("pas", pas.intValue()); } else faltando.add("PA sistólica");
        if (fc != null) { componentes.put("frequenciaCardiaca", regras.scoreCardiaco(fc.intValue())); valores.put("fc", fc.intValue()); } else faltando.add("FC");

        // O2 e consciencia so entram quando registrados (afericoes antigas nao tem o campo).
        boolean parcial = a.o2() == null || a.consciencia() == null;
        if (a.o2() != null) { componentes.put("oxigenioSuplementar", a.o2() ? 2 : 0); valores.put("o2Suplementar", a.o2()); }
        if (a.consciencia() != null) {
            String c = a.consciencia().trim().toUpperCase();
            componentes.put("consciencia", "A".equals(c) ? 0 : 3);
            valores.put("consciencia", c);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        LocalDateTime quando = dataHora(a.data(), a.hora());
        r.put("aferidoEm", quando != null ? quando.toString() : (a.data() + " " + a.hora()).trim());
        if (quando != null) {
            long horas = Duration.between(quando, agora).toHours();
            r.put("horasDesdeAfericao", horas);
            r.put("afericaoAtrasada", horas >= HORAS_AFERICAO_ATRASADA);
        }
        r.put("valores", valores);
        r.put("faltando", faltando);
        r.put("aviso", parcial ? AVISO_PARCIAL : null);
        r.put("qsofa", qsofa(fr, pas, a.consciencia()));

        if (5 - faltando.size() < MINIMO_PARAMETROS) { // conta so os 5 sinais medidos
            r.put("total", null);
            r.put("risco", "INSUFICIENTE");
            r.put("orientacao", "Sinais vitais incompletos para calcular o NEWS2. Registre FR, SpO2, PA, FC e temperatura.");
            r.put("componentes", componentes);
            return r;
        }
        int total = componentes.values().stream().mapToInt(Integer::intValue).sum();
        String risco = regras.riscoNews2(total, componentes);
        r.put("total", total);
        r.put("risco", risco);
        r.put("orientacao", regras.orientacaoNews2(risco));
        r.put("componentes", componentes);
        return r;
    }

    /**
     * qSOFA (Sepsis-3) com os sinais da beira-leito: FR >= 22, PAS <= 100 e alteracao do nivel
     * de consciencia. Escore >= 2 indica maior risco de desfecho grave SE houver infeccao; nao
     * diagnostica sepse. Consciencia nao registrada fica como pendente (pode faltar 1 ponto).
     */
    static Map<String, Object> qsofa(Double fr, Double pas, String consciencia) {
        List<String> criterios = new ArrayList<>();
        List<String> naoAvaliados = new ArrayList<>();
        if (fr == null) naoAvaliados.add("FR"); else if (fr >= 22) criterios.add("FR ≥ 22 (" + fr.intValue() + ")");
        if (pas == null) naoAvaliados.add("PA sistólica"); else if (pas <= 100) criterios.add("PAS ≤ 100 (" + pas.intValue() + ")");
        String c = consciencia == null ? "" : consciencia.trim().toUpperCase();
        if (c.isEmpty()) naoAvaliados.add("Consciência"); else if (!"A".equals(c)) criterios.add("Alteração da consciência (" + c + ")");

        Map<String, Object> q = new LinkedHashMap<>();
        q.put("total", criterios.size());
        q.put("positivo", criterios.size() >= 2);
        // Com 1 criterio e algo nao avaliado, o paciente pode ser qSOFA >= 2: avaliar o que falta.
        q.put("possivel", criterios.size() == 1 && !naoAvaliados.isEmpty());
        q.put("criterios", criterios);
        q.put("naoAvaliados", naoAvaliados);
        q.put("orientacao", criterios.size() >= 2
                ? "qSOFA ≥ 2: se houver suspeita de infecção, maior risco de desfecho grave. "
                        + "Avaliar o paciente e considerar o protocolo de sepse da instituição."
                : null);
        return q;
    }

    static String tendencia(Object atual, Object anterior) {
        if (!(atual instanceof Integer a) || !(anterior instanceof Integer b)) return "SEM_COMPARACAO";
        if (a - b >= 2) return "PIORA";
        if (b - a >= 2) return "MELHORA";
        return "ESTAVEL";
    }

    static Double faixa(Double v, double min, double max) {
        return v == null || v < min || v > max ? null : v;
    }

    static LocalDateTime dataHora(String data, String hora) {
        LocalDate d = null;
        String s = texto(data);
        for (DateTimeFormatter f : List.of(BR, DateTimeFormatter.ISO_LOCAL_DATE, BR_CURTO)) {
            try {
                d = LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s, f);
                break;
            } catch (RuntimeException ignorado) {
                // tenta o proximo formato
            }
        }
        if (d == null) return null;
        try {
            String h = texto(hora);
            return d.atTime(LocalTime.parse(h.length() >= 5 ? h.substring(0, 5) : h));
        } catch (RuntimeException e) {
            return d.atStartOfDay();
        }
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Double numero(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
