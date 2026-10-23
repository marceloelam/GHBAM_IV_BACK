package br.com.iv.imagemia.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import br.com.iv.imagemia.entity.ImagemIaAnalise;

/** Projecao de leitura de uma analise de imagem por IA para o frontend. */
public record ImagemIaAnaliseDto(
        Long id,
        Integer idbam,
        String idimagens,
        String descricaoExame,
        String modelo,
        String versaoModelo,
        String achado,
        String severidade,
        BigDecimal score,
        String status,
        String erro,
        LocalDateTime criadoEm,
        boolean ciente,
        LocalDateTime cienciaEm,
        String cienciaNome,
        String cienciaConduta,
        java.util.Map<String, Double> principais,
        boolean temMapaCalor) {

    /** Tomografia roda em segundo plano; PENDENTE alem disso = processamento perdido. */
    public static final int MINUTOS_MAX_PENDENTE = 30;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** "top" do servico (achado -> probabilidade, maior primeiro); vazio se nao houver. */
    private static java.util.Map<String, Double> principais(String detalhesJson) {
        java.util.Map<String, Double> m = new java.util.LinkedHashMap<>();
        if (detalhesJson == null || detalhesJson.isBlank()) return m;
        try {
            JSON.readTree(detalhesJson).path("top").fields()
                    .forEachRemaining(e -> m.put(e.getKey(), e.getValue().asDouble()));
        } catch (Exception ignore) {
            // detalhes antigos ou invalidos: so nao mostra a lista
        }
        return m;
    }

    private static boolean temMapa(String detalhesJson) {
        return detalhesJson != null && detalhesJson.contains("\"mapa_calor_jpg\":\"");
    }

    public static ImagemIaAnaliseDto de(ImagemIaAnalise a) {
        String status = a.status;
        String erro = a.erro;
        if ("PENDENTE".equals(status) && a.criadoEm != null
                && a.criadoEm.isBefore(LocalDateTime.now().minusMinutes(MINUTOS_MAX_PENDENTE))) {
            // So na leitura: nao reescreve o registro original.
            status = "ERRO";
            erro = "Processamento interrompido (backend reiniciado ou servico de IA fora do ar). Analise novamente.";
        }
        return new ImagemIaAnaliseDto(a.id, a.idbam, a.idimagens, a.descricaoExame, a.modelo, a.versaoModelo,
                a.achado, a.severidade, a.score, status, erro, a.criadoEm,
                a.cienciaEm != null, a.cienciaEm, a.cienciaNome, a.cienciaConduta,
                principais(a.detalhesJson), temMapa(a.detalhesJson));
    }
}
