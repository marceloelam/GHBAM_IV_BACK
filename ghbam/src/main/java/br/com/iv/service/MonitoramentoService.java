package br.com.iv.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import br.com.iv.dto.MonitoramentoDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ApplicationScoped
public class MonitoramentoService {

    @Inject
    EntityManager em;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public MonitoramentoDto monitorar(String unidade) {
        MonitoramentoDto dto = new MonitoramentoDto();
        dto.setUnidade(unidade);
        dto.setAtualizadoEm(LocalDateTime.now().format(FMT));

        LocalDate hoje = LocalDate.now();

        int aguardando = intVal(scalar(
            "SELECT COUNT(*) FROM bam b WHERE b.unidade = :u AND CAST(b.data AS DATE) = :hoje "
            + "AND NOT EXISTS (SELECT 1 FROM atendimento a WHERE a.bam = b.idbam)",
            unidade, hoje));
        dto.setPacientesAguardando(aguardando);

        int criticos = intVal(scalar(
            // bam.cor: 1 = VERMELHO (mesma escala de RagPacienteService/JornadaPaService)
            "SELECT COUNT(*) FROM bam b "
            + "WHERE b.unidade = :u AND CAST(b.data AS DATE) = :hoje AND b.cor = 1 "
            + "AND NOT EXISTS (SELECT 1 FROM atendimento a WHERE a.bam = b.idbam)",
            unidade, hoje));
        dto.setCasosCriticos(criticos);

        // Janela movel de 24 h (e nao "hoje"): na virada do dia o card ficava vazio ate o 1o BAM.
        // NULLIF evita erro de CAST com hora em branco; diferenca negativa ou > 24 h e dado errado.
        BigDecimal tempoMedioAtend = scalar(
            "SELECT AVG(m) FROM (SELECT EXTRACT(EPOCH FROM ((CAST(a.data AS DATE) + CAST(NULLIF(a.hora, '') AS TIME)) "
            + "- (CAST(b.data AS DATE) + CAST(NULLIF(b.hora, '') AS TIME)))) / 60 AS m, "
            + "CAST(b.data AS DATE) + CAST(NULLIF(b.hora, '') AS TIME) AS ts "
            + "FROM atendimento a JOIN bam b ON b.idbam = a.bam WHERE b.unidade = :u "
            + "AND CAST(b.data AS DATE) >= CURRENT_DATE - 1) t "
            + "WHERE m >= 0 AND m < 1440 AND ts >= now() - INTERVAL '24 hours'",
            unidade, hoje);
        dto.setTempoMedioAtendimentoMin(arredondar(tempoMedioAtend));

        BigDecimal permanencia = scalar(
            "SELECT AVG(CURRENT_DATE - CAST(diainternacao AS DATE)) FROM novointernado "
            + "WHERE unidade = :u AND alta = false AND diainternacao IS NOT NULL",
            unidade, hoje);
        dto.setTempoPermanenciaMediaDias(arredondar(permanencia));

        BigDecimal leitos = scalar(
            "SELECT COUNT(*) FROM localcenso WHERE unidade = :u AND excluido = false AND disponivel = true",
            unidade, hoje);
        BigDecimal internados = scalar(
            "SELECT COUNT(*) FROM novointernado n JOIN localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
            + "WHERE n.unidade = :u AND n.alta = false AND l.excluido = false AND l.disponivel = true",
            unidade, hoje);

        int totalLeitos = intVal(leitos);
        int leitosOcupados = intVal(internados);
        dto.setTotalLeitos(totalLeitos);
        dto.setLeitosOcupados(leitosOcupados);

        if (leitos != null && leitos.compareTo(BigDecimal.ZERO) > 0 && internados != null) {
            BigDecimal taxa = internados.multiply(BigDecimal.valueOf(100))
                .divide(leitos, 1, RoundingMode.HALF_UP);
            dto.setTaxaOcupacaoPct(taxa);

            if (taxa.compareTo(BigDecimal.valueOf(95)) >= 0) {
                dto.getAlertas().add(new MonitoramentoDto.Alerta("critico",
                    "Superlotação crítica",
                    "Taxa de ocupação em " + taxa + "% (" + leitosOcupados + "/" + totalLeitos + " leitos)"));
            } else if (taxa.compareTo(BigDecimal.valueOf(85)) >= 0) {
                dto.getAlertas().add(new MonitoramentoDto.Alerta("atencao",
                    "Ocupação elevada",
                    "Taxa de ocupação em " + taxa + "% (" + leitosOcupados + "/" + totalLeitos + " leitos)"));
            }
        }

        if (criticos > 0) {
            dto.getAlertas().add(new MonitoramentoDto.Alerta("critico",
                "Casos críticos aguardando",
                criticos + " paciente(s) crítico(s) sem atendimento médico hoje"));
        }

        if (aguardando > 20) {
            dto.getAlertas().add(new MonitoramentoDto.Alerta("atencao",
                "Fila longa",
                aguardando + " paciente(s) aguardando atendimento"));
        }

        if (dto.getAlertas().isEmpty()) {
            dto.getAlertas().add(new MonitoramentoDto.Alerta("bom",
                "Situação normal", "Indicadores dentro dos parâmetros."));
        }

        carregarSetores(dto, unidade);

        return dto;
    }

    private void carregarSetores(MonitoramentoDto dto, String unidade) {
        String sql = "SELECT COALESCE(s.setor, 'SEM SETOR'), "
            + "COUNT(n.idninternado), "
            + "COUNT(DISTINCT l.idsetorlocalcenso) "
            + "FROM setorcenso s "
            + "LEFT JOIN localcenso l ON l.setorcenso_idsetorcenso = s.idsetorcenso AND l.excluido = false AND l.disponivel = true "
            + "LEFT JOIN novointernado n ON n.local_idsetorlocalcenso = l.idsetorlocalcenso AND n.alta = false AND n.unidade = :u "
            + "WHERE s.unidade = :u AND s.excluido = false "
            + "GROUP BY s.setor ORDER BY COUNT(n.idninternado) DESC, s.setor";
        try {
            Query q = em.createNativeQuery(sql);
            q.setParameter("u", unidade);
            @SuppressWarnings("unchecked")
            java.util.List<Object[]> rows = q.getResultList();
            for (Object[] row : rows) {
                String setor = row[0] == null ? "SEM SETOR" : String.valueOf(row[0]);
                int ocupados = intVal(numero(row[1]));
                int leitos = intVal(numero(row[2]));
                BigDecimal pct = leitos > 0
                    ? BigDecimal.valueOf(ocupados * 100.0 / leitos).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                dto.getSetores().add(new MonitoramentoDto.SetorOcupacao(setor, ocupados, leitos, pct));
            }
        } catch (Exception ignored) {}
    }

    private BigDecimal scalar(String sql, String unidade, LocalDate hoje) {
        try {
            Query q = em.createNativeQuery(sql);
            q.setParameter("u", unidade);
            if (sql.contains(":hoje")) q.setParameter("hoje", hoje);
            return numero(q.getSingleResult());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal numero(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        return new BigDecimal(String.valueOf(v));
    }

    private int intVal(BigDecimal v) {
        return v == null ? 0 : v.intValue();
    }

    private BigDecimal arredondar(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }
}
