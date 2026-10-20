package br.com.iv.cdss.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Indicadores de seguranca do paciente da unidade da sessao, a partir do que o
 * GHBAM registra: administracao no horario, overrides de alerta critico, ciencia
 * de valor critico, passagem de plantao recebida, dispositivos-dia e paciente-dia.
 * So leitura; cada bloco e calculado de forma independente (falha num bloco nao
 * derruba os demais).
 */
@ApplicationScoped
public class IndicadoresSegurancaService {
    static final int TOLERANCIA_MIN = 30;

    @Inject
    EntityManager em;

    @Inject
    DispositivoInvasivoService dispositivoService;

    public Map<String, Object> montar(String inicio, String fim, AuthenticatedUser usuario) {
        PlantaoEnfermagemService.exigirProfissional(usuario);
        LocalDate de;
        LocalDate ate;
        try {
            de = LocalDate.parse(inicio == null ? "" : inicio.trim());
            ate = LocalDate.parse(fim == null ? "" : fim.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Periodo invalido; use aaaa-mm-dd.");
        }
        if (ate.isBefore(de) || ChronoUnit.DAYS.between(de, ate) > 366) {
            throw new IllegalArgumentException("Periodo invalido (maximo de um ano).");
        }
        String unidade = usuario.unidade().trim();
        LocalDateTime ini = de.atStartOfDay();
        LocalDateTime fimExclusivo = ate.plusDays(1).atStartOfDay();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("unidade", unidade);
        r.put("inicio", de);
        r.put("fim", ate);
        r.put("administracaoNoHorario", bloco(() -> administracaoNoHorario(unidade, ini, fimExclusivo)));
        r.put("alertasCriticos", bloco(() -> overrides(unidade, ini, fimExclusivo)));
        r.put("passagemPlantao", bloco(() -> passagens(unidade, ini, fimExclusivo)));
        Object pacientesDia = bloco(() -> Map.of("valor", pacientesDia(unidade, de, ate, LocalDate.now()),
                "nota", "Estimado pela internacao (diainternacao) ate a alta (datadaacao) ou hoje."));
        r.put("pacientesDia", pacientesDia);
        r.put("dispositivos", bloco(() -> dispositivos(de, ate, usuario, pacientesDia)));
        return r;
    }

    private Object bloco(Supplier<Object> calculo) {
        try {
            return calculo.get();
        } catch (RuntimeException e) {
            return Map.of("erro", "Indicador indisponivel: " + e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> administracaoNoHorario(String unidade, LocalDateTime ini, LocalDateTime fim) {
        List<Object[]> linhas = em.createNativeQuery(
                "select a.aprazado_em, a.horario_aprazado, a.checado_em from prescricao_aprazamento_checagem a "
                        + "where a.checado = true and a.checado_em >= :ini and a.checado_em < :fim "
                        + "and a.idbam in (select n.nbam from novointernado n where n.unidade = :unidade)")
                .setParameter("ini", ini).setParameter("fim", fim).setParameter("unidade", unidade)
                .getResultList();
        int noHorario = 0;
        int atrasadas = 0;
        int semReferencia = 0;
        for (Object[] l : linhas) {
            String classe = classificarChecagem(dataHora(l[0]), (String) l[1], dataHora(l[2]));
            switch (classe) {
                case "NO_HORARIO" -> noHorario++;
                case "ATRASADA" -> atrasadas++;
                default -> semReferencia++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checagens", linhas.size());
        m.put("noHorario", noHorario);
        m.put("atrasadas", atrasadas);
        m.put("semReferencia", semReferencia);
        m.put("percentualNoHorario", percentual(noHorario, noHorario + atrasadas));
        m.put("toleranciaMinutos", TOLERANCIA_MIN);
        return m;
    }

    /** NO_HORARIO (ate 30 min apos o aprazado, ou antes), ATRASADA, ou SEM_REFERENCIA. */
    static String classificarChecagem(LocalDateTime aprazadoEm, String horario, LocalDateTime checadoEm) {
        LocalDateTime previsto = PlantaoEnfermagemService.horarioPrevisto(aprazadoEm, horario);
        if (previsto == null || checadoEm == null) return "SEM_REFERENCIA";
        return checadoEm.isAfter(previsto.plusMinutes(TOLERANCIA_MIN)) ? "ATRASADA" : "NO_HORARIO";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> overrides(String unidade, LocalDateTime ini, LocalDateTime fim) {
        List<Object[]> linhas = em.createNativeQuery(
                "select coalesce(o.modulo_origem, '-'), coalesce(o.alerta_codigo, '-'), count(*) "
                        + "from cdss_alerta_override o where o.unidade = :unidade and o.criado_em >= :ini and o.criado_em < :fim "
                        + "group by 1, 2 order by 3 desc")
                .setParameter("unidade", unidade).setParameter("ini", ini).setParameter("fim", fim)
                .getResultList();
        long overridesPrescricao = 0;
        long cienciasCritico = 0;
        long reconciliacoes = 0;
        List<Map<String, Object>> principais = new ArrayList<>();
        for (Object[] l : linhas) {
            String modulo = (String) l[0];
            String codigo = (String) l[1];
            long qtd = ((Number) l[2]).longValue();
            if (ResultadoCriticoService.MODULO.equals(modulo)) {
                cienciasCritico += qtd;
            } else if (ReconciliacaoMedicamentosService.MODULO.equals(modulo)) {
                reconciliacoes += qtd;
            } else {
                overridesPrescricao += qtd;
                if (principais.size() < 5) {
                    principais.add(Map.of("codigo", codigo, "quantidade", qtd));
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("overridesComJustificativa", overridesPrescricao);
        m.put("principaisAlertasSobrepostos", principais);
        m.put("cienciasValorCritico", cienciasCritico);
        m.put("decisoesReconciliacao", reconciliacoes);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> passagens(String unidade, LocalDateTime ini, LocalDateTime fim) {
        Object[] l = (Object[]) em.createNativeQuery(
                "select count(*), count(p.recebido_em) from passagem_plantao p "
                        + "where p.unidade = :unidade and p.entregue_em >= :ini and p.entregue_em < :fim")
                .setParameter("unidade", unidade).setParameter("ini", ini).setParameter("fim", fim)
                .getSingleResult();
        long total = ((Number) l[0]).longValue();
        long recebidas = ((Number) l[1]).longValue();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("registradas", total);
        m.put("recebidas", recebidas);
        m.put("percentualRecebidas", percentual(recebidas, total));
        return m;
    }

    @SuppressWarnings("unchecked")
    long pacientesDia(String unidade, LocalDate de, LocalDate ate, LocalDate hoje) {
        List<Object[]> linhas = em.createNativeQuery(
                "select n.nbam, min(n.diainternacao), bool_or(n.alta), max(n.datadaacao) from novointernado n "
                        + "where n.unidade = :unidade and n.diainternacao is not null and n.diainternacao <= :ate "
                        + "group by n.nbam")
                .setParameter("unidade", unidade).setParameter("ate", ate)
                .getResultList();
        long total = 0;
        for (Object[] l : linhas) {
            LocalDate entrada = data(l[1]);
            boolean alta = Boolean.TRUE.equals(l[2]);
            LocalDate saida = alta ? data(l[3]) : null;
            if (alta && saida == null) continue; // alta sem data: nao da para estimar
            total += diasInternado(entrada, saida, de, ate, hoje);
        }
        return total;
    }

    /** Dias-calendario de internacao dentro de [de, ate]; o dia da alta nao conta. */
    static long diasInternado(LocalDate entrada, LocalDate saida, LocalDate de, LocalDate ate, LocalDate hoje) {
        if (entrada == null) return 0;
        LocalDate inicio = entrada.isAfter(de) ? entrada : de;
        LocalDate ultimo = saida == null ? hoje : saida.minusDays(1);
        LocalDate fim = ultimo.isBefore(ate) ? ultimo : ate;
        return fim.isBefore(inicio) ? 0 : ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dispositivos(LocalDate de, LocalDate ate, AuthenticatedUser usuario, Object pacientesDia) {
        Map<String, Object> base = dispositivoService.indicadores(de.toString(), ate.toString(), usuario);
        Long pd = null;
        if (pacientesDia instanceof Map<?, ?> m && m.get("valor") instanceof Number n) {
            pd = n.longValue();
        }
        List<Map<String, Object>> tipos = new ArrayList<>();
        for (Map<String, Object> t : (List<Map<String, Object>>) base.get("tipos")) {
            Map<String, Object> linha = new LinkedHashMap<>(t);
            long dd = ((Number) t.get("dispositivosDia")).longValue();
            linha.put("taxaUtilizacao", pd == null || pd == 0 ? null : Math.round(dd * 1000.0 / pd) / 1000.0);
            tipos.add(linha);
        }
        return Map.of("tipos", tipos);
    }

    private static Double percentual(long parte, long total) {
        return total == 0 ? null : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private static LocalDateTime dataHora(Object v) {
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        if (v instanceof LocalDateTime l) return l;
        return null;
    }

    private static LocalDate data(Object v) {
        if (v instanceof Date d) return d.toLocalDate();
        if (v instanceof LocalDate l) return l;
        if (v instanceof Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (v instanceof LocalDateTime l) return l.toLocalDate();
        return null;
    }
}
