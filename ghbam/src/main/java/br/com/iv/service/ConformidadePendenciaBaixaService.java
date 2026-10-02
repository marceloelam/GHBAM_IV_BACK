package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import br.com.iv.entity.ConformidadePendenciaBaixa;
import br.com.iv.entity.Bam;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConformidadePendenciaBaixaService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    EntityManager entityManager;

    public List<ConformidadePendenciaBaixa> listarPorBam(int idbam) {
        return ConformidadePendenciaBaixa
                .list("idbam = ?1 order by criadoEm desc", idbam);
    }

    public Map<String, ConformidadePendenciaBaixa> ultimasPorChave(int idbam) {
        return listarPorBam(idbam).stream()
                .collect(Collectors.toMap(
                        ConformidadePendenciaBaixa::getChavePendencia,
                        item -> item,
                        (atual, ignorado) -> atual,
                        LinkedHashMap::new));
    }

    public boolean estaBaixada(Map<String, ConformidadePendenciaBaixa> baixas, String chave) {
        ConformidadePendenciaBaixa baixa = baixas.get(chave);
        if (baixa == null) return false;
        String status = String.valueOf(baixa.getStatus()).toUpperCase();
        return "RESOLVIDA".equals(status)
                || "TRATADA".equals(status)
                || "JUSTIFICADA".equals(status)
                || "IGNORADA".equals(status);
    }

    @Transactional
    public ConformidadePendenciaBaixa registrar(Map<String, Object> dto) {
        return registrar(dto, null);
    }

    @Transactional
    public ConformidadePendenciaBaixa registrar(Map<String, Object> dto, String unidadeLogada) {
        int idbam = numero(dto.get("idbam"));
        validarAcessoBam(idbam, unidadeLogada);

        ConformidadePendenciaBaixa baixa = new ConformidadePendenciaBaixa();
        baixa.setIdbam(idbam);
        baixa.setUnidade(unidadeDaBaixa(idbam, unidadeLogada));
        baixa.setChavePendencia(texto(dto.get("chavePendencia")));
        baixa.setTitulo(texto(dto.get("titulo")));
        baixa.setTipo(textoOuPadrao(dto.get("tipo"), "Operacional"));
        baixa.setStatus(normalizarStatus(dto.get("status")));
        baixa.setObservacao(texto(dto.get("observacao")));
        baixa.setResponsavel(texto(dto.get("responsavel")));
        baixa.setResponsavelMatricula(texto(dto.get("responsavelMatricula")));
        baixa.setCriadoEm(LocalDateTime.now());
        baixa.persist();
        return baixa;
    }

    public void validarAcessoBam(int idbam, String unidadeLogada) {
        if (idbam <= 0) {
            throw new BadRequestException("BAM invalido.");
        }
        if (Bam.findById(idbam) == null) {
            throw new BadRequestException("BAM nao encontrado.");
        }
    }

    public Map<String, Object> toMap(ConformidadePendenciaBaixa baixa) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idbaixa", baixa.getIdbaixa());
        item.put("idbam", baixa.getIdbam());
        item.put("unidade", baixa.getUnidade());
        item.put("chavePendencia", baixa.getChavePendencia());
        item.put("titulo", baixa.getTitulo());
        item.put("tipo", baixa.getTipo());
        item.put("status", baixa.getStatus());
        item.put("observacao", baixa.getObservacao());
        item.put("responsavel", baixa.getResponsavel());
        item.put("responsavelMatricula", baixa.getResponsavelMatricula());
        item.put("criadoEm", baixa.getCriadoEm());
        return item;
    }

    public Map<String, Object> relatorio(LocalDate inicio, LocalDate fim, String unidade, int limite) {
        LocalDate dataInicio = inicio == null ? LocalDate.now() : inicio;
        LocalDate dataFim = fim == null ? dataInicio : fim;
        if (dataFim.isBefore(dataInicio)) {
            LocalDate temp = dataInicio;
            dataInicio = dataFim;
            dataFim = temp;
        }

        String filtroUnidade = unidade == null ? "" : unidade.split(":")[0].trim();
        LocalDateTime inicioPeriodo = dataInicio.atStartOfDay();
        LocalDateTime fimPeriodo = dataFim.plusDays(1).atStartOfDay().minusNanos(1);

        StringBuilder jpql = new StringBuilder("""
                select baixa, coalesce(baixa.unidade, bam.unidade), bam.data, bam.hora, bam.paciente.nome
                from ConformidadePendenciaBaixa baixa
                left join Bam bam on bam.idbam = baixa.idbam
                where baixa.criadoEm between :inicio and :fim
                """);
        if (!filtroUnidade.isBlank() && !"TODAS".equalsIgnoreCase(filtroUnidade)) {
            jpql.append(" and upper(coalesce(baixa.unidade, bam.unidade, '')) = upper(:unidade)");
        }
        jpql.append(" order by baixa.criadoEm desc");

        var query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("inicio", inicioPeriodo)
                .setParameter("fim", fimPeriodo)
                .setMaxResults(normalizarLimite(limite));
        if (!filtroUnidade.isBlank() && !"TODAS".equalsIgnoreCase(filtroUnidade)) {
            query.setParameter("unidade", filtroUnidade);
        }

        List<Map<String, Object>> itens = query.getResultList().stream()
                .map(row -> {
                    Map<String, Object> item = toMap((ConformidadePendenciaBaixa) row[0]);
                    item.put("unidade", row[1]);
                    item.put("data", row[2]);
                    item.put("hora", row[3]);
                    item.put("paciente", row[4]);
                    return item;
                })
                .toList();

        Map<String, Long> porStatus = itens.stream()
                .collect(Collectors.groupingBy(item -> textoOuPadrao(item.get("status"), "SEM_STATUS"), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> porResponsavel = itens.stream()
                .collect(Collectors.groupingBy(item -> textoOuPadrao(item.get("responsavel"), "Sem responsavel"), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> porTipo = itens.stream()
                .collect(Collectors.groupingBy(item -> textoOuPadrao(item.get("tipo"), "Sem tipo"), LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("inicio", dataInicio);
        filtros.put("fim", dataFim);
        filtros.put("unidade", filtroUnidade.isBlank() ? "TODAS" : filtroUnidade);
        filtros.put("limite", normalizarLimite(limite));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filtros", filtros);
        response.put("total", itens.size());
        response.put("porStatus", porStatus);
        response.put("porResponsavel", porResponsavel);
        response.put("porTipo", porTipo);
        response.put("itens", itens);
        return response;
    }

    private String normalizarStatus(Object value) {
        String status = texto(value).toUpperCase();
        if ("RESOLVIDA".equals(status)
                || "TRATADA".equals(status)
                || "JUSTIFICADA".equals(status)
                || "IGNORADA".equals(status)
                || "REABERTA".equals(status)) return status;
        return "RESOLVIDA";
    }

    private String textoOuPadrao(Object value, String padrao) {
        String result = texto(value);
        return result.isBlank() ? padrao : result;
    }

    private String texto(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer numero(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private int normalizarLimite(int limite) {
        if (limite <= 0) return 50;
        return Math.min(limite, 200);
    }

    private String unidadeDaBaixa(int idbam, String unidadeLogada) {
        String unidadePermitida = unidadeNormalizada(unidadeLogada);
        if (!unidadePermitida.isBlank()) {
            return unidadePermitida;
        }

        Bam bam = Bam.findById(idbam);
        return bam == null ? "" : unidadeNormalizada(bam.getUnidade());
    }

    private String unidadeNormalizada(String unidade) {
        if (unidade == null) return "";
        return unidade.split(":")[0].trim();
    }
}
