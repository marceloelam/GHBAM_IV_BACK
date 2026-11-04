package br.com.iv.service;

import br.com.iv.cdss.service.DeterioracaoNews2Service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Quadro de fluxo de pacientes da unidade (/base2/fluxo-pacientes): todos os BAMs abertos
 * por etapa, com tempo de espera e tempo-alvo de Manchester. Uma consulta para a lista e
 * duas agregadas (internacao, exames pendentes), sem foto nem texto clinico.
 * A etapa vem de SituacaoPacienteService.classificar, a mesma regra da busca do cabecalho.
 */
@ApplicationScoped
public class FluxoPacientesService {

    /** BAMs abertos chegados ate ha tantos dias; alem disso o paciente quase sempre saiu sem alta registrada. */
    private static final int JANELA_DIAS = 2;
    /** BAM aberto ha tantas horas ganha alerta de possivel saida sem registro. Continua no quadro: pode ainda estar na unidade. */
    private static final int HORAS_ALERTA_ABERTO = 12;

    private static final Logger LOG = Logger.getLogger(FluxoPacientesService.class);

    @Inject
    EntityManager em;

    @Inject
    DeterioracaoNews2Service news2;

    public Map<String, Object> quadro(String unidade) {
        ZoneId zona = ZoneId.systemDefault();
        LocalDateTime agora = LocalDateTime.now(zona);
        Date desde = Date.from(agora.toLocalDate().minusDays(JANELA_DIAS).atStartOfDay(zona).toInstant());

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createQuery(
                "select b.idbam, b.data, b.hora, b.cor, b.ematendimento, a.cor, a.risco, a.data, a.hora, a.esperando, "
                        + "t.tratado, t.tipo_alta, t.destino, t.data, t.hora, r.setor, r.atendidopor, p.idpaciente, p.nome, p.idade, p.sexo "
                        + "from Bam b join b.avaliacao a join b.tratamento t join b.registro r join b.paciente p "
                        // Quem ocupa leito ou saiu dele hoje entra mesmo tendo chegado ha mais tempo que a janela.
                        + "where b.unidade = ?1 and ((b.data >= ?2 and r.eletivo = false) or b.idbam in "
                        + "(select n.nbam from NovoInternado n where n.unidade = ?1 and (n.alta = false or n.datadaacao = ?3)))")
                .setParameter(1, unidade)
                .setParameter(2, desde)
                .setParameter(3, agora.toLocalDate())
                .getResultList();

        List<Integer> ids = new ArrayList<>();
        for (Object[] l : linhas) ids.add((Integer) l[0]);
        Map<Integer, Object[]> internacoes = internacoes(ids);
        Map<Integer, Long> exames = examesPendentes(ids);

        List<Map<String, Object>> pacientes = new ArrayList<>();
        for (Object[] l : linhas) {
            int idbam = (Integer) l[0];
            LocalDateTime chegada = momento(l[1] == null ? null : new Date(((Date) l[1]).getTime()).toInstant().atZone(zona).toLocalDate(), (String) l[2]);
            int cor = numero(l[3]) > 0 ? numero(l[3]) : numero(l[5]);
            String risco = texto((String) l[6]);
            if (cor <= 0) cor = corDoRisco(risco);
            boolean classificado = risco != null || cor > 0;
            Object[] ni = internacoes.get(idbam);
            boolean temInternacao = ni != null;
            boolean internacaoComAlta = ni != null && Boolean.TRUE.equals(ni[1]);
            String status = SituacaoPacienteService.classificar(temInternacao, internacaoComAlta, numero(l[10]) == 1,
                    (String) l[11], Boolean.TRUE.equals(l[4]), numero(l[9]) == 1, classificado);

            boolean encerrado = "SAIU".equals(status);
            boolean noLeito = "INTERNADO".equals(status);
            // A coluna de saidas e do dia: zera a meia-noite.
            LocalDate diaSaida = internacaoComAlta && ni[5] instanceof LocalDate altaLeito ? altaLeito : data((String) l[13]);
            if (encerrado && !saidaDeHoje(diaSaida, chegada, agora.toLocalDate())) continue;

            Map<String, Object> c = new LinkedHashMap<>();
            c.put("idbam", idbam);
            c.put("idpaciente", l[17]);
            c.put("nome", texto((String) l[18]));
            c.put("idade", l[19]);
            c.put("sexo", texto((String) l[20]));
            c.put("etapa", status);
            c.put("cor", cor);
            c.put("setor", texto((String) l[15]));
            c.put("chegada", chegada == null ? null : chegada.toString());
            c.put("minutosNaUnidade", minutos(chegada, agora));

            LocalDateTime classificacao = momento(data((String) l[7]), (String) l[8]);
            c.put("classificacao", classificacao == null ? null : classificacao.toString());
            if ("AGUARDANDO_MEDICO".equals(status)) {
                // Espera pelo medico conta da classificacao (inicio do tempo-alvo de Manchester).
                Long espera = minutos(classificacao != null ? classificacao : chegada, agora);
                Integer alvo = alvoManchester(cor);
                c.put("minutosEspera", espera);
                c.put("alvoMinutos", alvo);
                c.put("atrasado", espera != null && alvo != null && espera > alvo);
            } else if ("AGUARDANDO_CLASSIFICACAO".equals(status)) {
                c.put("minutosEspera", minutos(chegada, agora));
            }
            if ("EM_ATENDIMENTO".equals(status)) c.put("atendidoPor", texto((String) l[16]));
            if (!encerrado) c.put("examesPendentes", exames.getOrDefault(idbam, 0L));
            if (encerrado) {
                String destino = texto((String) l[11]);
                if (destino == null || "-".equals(destino)) destino = texto((String) l[12]);
                c.put("destino", destino);
                if (ni != null) c.put("leito", juntar((String) ni[2], (String) ni[3], (String) ni[4]));
                LocalDateTime saida = momento(data((String) l[13]), (String) l[14]);
                c.put("saida", saida == null ? null : saida.toString());
            }
            if (noLeito) c.put("leito", juntar((String) ni[2], (String) ni[3], (String) ni[4]));
            if (!encerrado && !noLeito && chegada != null && Duration.between(chegada, agora).toHours() >= HORAS_ALERTA_ABERTO) {
                c.put("alerta", "BAM aberto há mais de " + HORAS_ALERTA_ABERTO + " horas: confira se o paciente segue na unidade ou saiu sem registro de alta.");
            }
            pacientes.add(c);
        }

        anexarNews2(pacientes);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("unidade", unidade);
        r.put("geradoEm", agora.toString());
        r.put("pacientes", pacientes);
        return r;
    }

    /**
     * NEWS2/qSOFA de quem ainda esta na unidade (sinais da enfermagem ou, sem eles, da triagem).
     * So o resumo para o cartao; o detalhe fica na tela de atendimento. Falha aqui nao derruba o quadro.
     */
    private void anexarNews2(List<Map<String, Object>> pacientes) {
        List<Integer> abertos = new ArrayList<>();
        for (Map<String, Object> c : pacientes) {
            if (!"SAIU".equals(c.get("etapa")) && !"INTERNADO".equals(c.get("etapa"))) abertos.add((Integer) c.get("idbam"));
        }
        if (abertos.isEmpty()) return;
        Map<Integer, Map<String, Object>> escores;
        try {
            escores = news2.doBams(abertos);
        } catch (RuntimeException e) {
            LOG.warnf("Quadro de fluxo sem NEWS2: %s", e.getMessage());
            return;
        }
        for (Map<String, Object> c : pacientes) {
            Map<String, Object> e = escores.get((Integer) c.get("idbam"));
            if (e == null || e.get("total") == null) continue;
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("total", e.get("total"));
            n.put("risco", e.get("risco"));
            n.put("tendencia", e.get("tendencia"));
            n.put("fonte", e.get("fonte"));
            n.put("aferidoEm", e.get("aferidoEm"));
            n.put("afericaoAtrasada", e.get("afericaoAtrasada"));
            n.put("qsofaPositivo", e.get("qsofa") instanceof Map<?, ?> q && Boolean.TRUE.equals(q.get("positivo")));
            c.put("news2", n);
        }
    }

    /** Tempo-alvo do primeiro atendimento medico (Manchester), em minutos: 1 azul .. 5 vermelho. */
    static Integer alvoManchester(int cor) {
        return switch (cor) {
            case 5 -> 0;
            case 4 -> 10;
            case 3 -> 60;
            case 2 -> 120;
            case 1 -> 240;
            default -> null;
        };
    }

    static int corDoRisco(String risco) {
        if (risco == null) return 0;
        return switch (risco.trim().toUpperCase()) {
            case "VERMELHO" -> 5;
            case "LARANJA" -> 4;
            case "AMARELO" -> 3;
            case "VERDE" -> 2;
            case "AZUL" -> 1;
            default -> 0;
        };
    }

    private Map<Integer, Object[]> internacoes(List<Integer> ids) {
        Map<Integer, Object[]> r = new HashMap<>();
        if (ids.isEmpty()) return r;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(
                "select n.nbam, n.alta, s.setor, lc.local, n.leitoocupado, n.datadaacao from NovoInternado n "
                        + "left join n.setor s left join n.local lc where n.nbam in ?1 order by n.alta asc, n.idninternado desc")
                .setParameter(1, ids)
                .getResultList();
        for (Object[] row : rows) r.putIfAbsent((Integer) row[0], row);
        return r;
    }

    private Map<Integer, Long> examesPendentes(List<Integer> ids) {
        Map<Integer, Long> r = new HashMap<>();
        if (ids.isEmpty()) return r;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(
                "select p.bam, count(p) from pedidomedico2 p where p.bam in ?1 and p.efetuado = false group by p.bam")
                .setParameter(1, ids)
                .getResultList();
        for (Object[] row : rows) r.put((Integer) row[0], (Long) row[1]);
        return r;
    }

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("yyyy/MM/dd"), DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    /** Datas gravadas como texto aparecem em mais de um formato (a recepcao grava aaaa/MM/dd). */
    /** Vale a data registrada da saida; sem ela, a da chegada. */
    static boolean saidaDeHoje(LocalDate saida, LocalDateTime chegada, LocalDate hoje) {
        LocalDate dia = saida != null ? saida : chegada == null ? null : chegada.toLocalDate();
        return hoje.equals(dia);
    }

    static LocalDate data(String v) {
        String s = texto(v);
        if (s == null) return null;
        if (s.length() > 10) s = s.substring(0, 10);
        for (DateTimeFormatter f : FORMATOS_DATA) {
            try {
                return LocalDate.parse(s, f);
            } catch (RuntimeException ignored) {
                // tenta o proximo formato
            }
        }
        return null;
    }

    static LocalDateTime momento(LocalDate dia, String hora) {
        if (dia == null) return null;
        String h = texto(hora);
        if (h == null) return null;
        try {
            return dia.atTime(LocalTime.parse(h.length() == 5 ? h + ":00" : h.substring(0, Math.min(8, h.length()))));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long minutos(LocalDateTime de, LocalDateTime ate) {
        if (de == null) return null;
        long m = Duration.between(de, ate).toMinutes();
        return m < 0 ? null : m;
    }

    private static int numero(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String juntar(String... partes) {
        List<String> ok = new ArrayList<>();
        for (String p : partes) if (p != null && !p.isBlank()) ok.add(p.trim());
        return ok.isEmpty() ? null : String.join(" / ", ok);
    }
}
