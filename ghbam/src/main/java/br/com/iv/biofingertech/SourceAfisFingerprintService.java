package br.com.iv.biofingertech;

import java.util.Base64;
import java.util.List;

import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintImageOptions;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;

import br.com.iv.entity.Servidor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SourceAfisFingerprintService {

    public static final String TEMPLATE_PREFIX = "SOURCEAFIS_V1:";
    private static final double DEFAULT_THRESHOLD = 40.0d;
    private static final double DEFAULT_DPI = 500.0d;
    private static final double AMBIGUITY_MARGIN = 10.0d;
    private static final int TEMPLATE_CACHE_MAX = 20000;
    // Desserializar o template custa mais que comparar; o cache evita refazer isso a cada login.
    private static final java.util.Map<String, FingerprintTemplate> TEMPLATE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public String createTemplateFromImage(String imageBase64, Double dpi) {
        byte[] imageBytes = decodeBase64Payload(imageBase64);
        if (imageBytes.length == 0) {
            throw new IllegalArgumentException("Imagem da digital vazia.");
        }
        FingerprintImageOptions options = new FingerprintImageOptions().dpi(validDpi(dpi));
        FingerprintTemplate template = new FingerprintTemplate(new FingerprintImage(imageBytes, options));
        return TEMPLATE_PREFIX + Base64.getEncoder().encodeToString(template.toByteArray());
    }

    public String normalizeTemplate(String value, Double dpi) {
        String safe = text(value);
        if (safe.isBlank()) {
            throw new IllegalArgumentException("Digital nao informada.");
        }
        if (isSourceAfisTemplate(safe)) {
            return safe;
        }
        return createTemplateFromImage(safe, dpi);
    }

    public MatchResult verify(String probeTemplateValue, String candidateTemplateValue, Double threshold) {
        FingerprintTemplate probe = readTemplate(probeTemplateValue);
        FingerprintTemplate candidate = readTemplate(candidateTemplateValue);
        double similarity = new FingerprintMatcher(probe).match(candidate);
        double minimum = threshold == null || threshold <= 0 ? DEFAULT_THRESHOLD : threshold;
        return new MatchResult(similarity >= minimum, similarity, minimum, null, null);
    }

    public MatchResult identify(String probeTemplateValue, List<Servidor> servidores, Double threshold) {
        FingerprintTemplate probe = readTemplate(probeTemplateValue);
        FingerprintMatcher matcher = new FingerprintMatcher(probe);
        double minimum = threshold == null || threshold <= 0 ? DEFAULT_THRESHOLD : threshold;
        double best = Double.NEGATIVE_INFINITY;
        Servidor bestServidor = null;

        for (Servidor servidor : servidores) {
            String templateValue = bestTemplate(servidor);
            if (!isSourceAfisTemplate(templateValue)) {
                continue;
            }
            try {
                double similarity = matcher.match(readTemplate(templateValue));
                if (similarity > best) {
                    best = similarity;
                    bestServidor = servidor;
                }
            } catch (RuntimeException ignored) {
                // Um template antigo/corrompido nao deve impedir a busca nos demais.
            }
        }

        boolean matched = bestServidor != null && best >= minimum;
        return new MatchResult(
                matched,
                best == Double.NEGATIVE_INFINITY ? 0.0d : best,
                minimum,
                bestServidor == null ? null : bestServidor.getMatricula(),
                bestServidor);
    }

    /**
     * Identificacao 1:N no proprio servidor usando a coluna biometria_sourceafis
     * (um ou mais modelos separados por ';'). Rejeita como ambiguo quando outro
     * servidor tambem passa do limiar com pontuacao proxima da melhor, para nao
     * logar a pessoa errada.
     */
    public MatchResult identifyServidores(String probeTemplateValue, List<Servidor> servidores, Double threshold) {
        FingerprintMatcher matcher = new FingerprintMatcher(readTemplate(probeTemplateValue));
        double minimum = threshold == null || threshold <= 0 ? DEFAULT_THRESHOLD : threshold;
        double best = Double.NEGATIVE_INFINITY;
        double secondBest = Double.NEGATIVE_INFINITY;
        Servidor bestServidor = null;

        for (Servidor servidor : servidores) {
            double score = Double.NEGATIVE_INFINITY;
            for (String templateValue : splitTemplates(servidor == null ? null : servidor.getBiometriaSourceafis())) {
                try {
                    score = Math.max(score, matcher.match(cachedTemplate(templateValue)));
                } catch (RuntimeException ignored) {
                    // Um template corrompido nao deve impedir a busca nos demais.
                }
            }
            if (score > best) {
                secondBest = best;
                best = score;
                bestServidor = servidor;
            } else if (score > secondBest) {
                secondBest = score;
            }
        }

        boolean ambiguo = secondBest >= minimum && best - secondBest < AMBIGUITY_MARGIN;
        boolean matched = bestServidor != null && best >= minimum && !ambiguo;
        if (!matched) {
            // So pontuacoes (sem identificar ninguem): base para calibrar o limiar com o leitor real.
            org.jboss.logging.Logger.getLogger(SourceAfisFingerprintService.class).infof(
                    "Digital nao reconhecida: melhor=%.1f segunda=%.1f limiar=%.1f ambiguo=%s candidatos=%d",
                    best == Double.NEGATIVE_INFINITY ? 0.0d : best, secondBest == Double.NEGATIVE_INFINITY ? 0.0d : secondBest,
                    minimum, ambiguo, servidores.size());
        }
        return new MatchResult(
                matched,
                best == Double.NEGATIVE_INFINITY ? 0.0d : best,
                minimum,
                bestServidor == null ? null : bestServidor.getMatricula(),
                bestServidor);
    }

    /** Gera os modelos a partir das imagens e junta no formato da coluna biometria_sourceafis. */
    public String createTemplatesFromImages(List<String> imagesBase64, Double dpi) {
        StringBuilder out = new StringBuilder();
        for (String image : imagesBase64) {
            if (text(image).isBlank()) continue;
            if (out.length() > 0) out.append(';');
            out.append(createTemplateFromImage(image, dpi));
        }
        return out.length() == 0 ? null : out.toString();
    }

    public List<String> splitTemplates(String value) {
        List<String> templates = new java.util.ArrayList<>();
        for (String part : text(value).split(";")) {
            if (isSourceAfisTemplate(part)) templates.add(part.trim());
        }
        return templates;
    }

    private FingerprintTemplate cachedTemplate(String value) {
        if (TEMPLATE_CACHE.size() > TEMPLATE_CACHE_MAX) {
            TEMPLATE_CACHE.clear();
        }
        return TEMPLATE_CACHE.computeIfAbsent(value, this::readTemplate);
    }

    public boolean isSourceAfisTemplate(String value) {
        return text(value).startsWith(TEMPLATE_PREFIX);
    }

    private FingerprintTemplate readTemplate(String value) {
        String safe = text(value);
        if (!isSourceAfisTemplate(safe)) {
            throw new IllegalArgumentException("Template SourceAFIS invalido ou ausente.");
        }
        String payload = safe.substring(TEMPLATE_PREFIX.length());
        return new FingerprintTemplate(Base64.getDecoder().decode(payload));
    }

    private String bestTemplate(Servidor servidor) {
        if (servidor == null) {
            return "";
        }
        String fir = text(servidor.getFir());
        return fir.isBlank() ? text(servidor.getBiometria()) : fir;
    }

    private byte[] decodeBase64Payload(String value) {
        String payload = text(value);
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma >= 0 && comma < payload.length() - 1) {
            payload = payload.substring(comma + 1);
        }
        payload = payload.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(payload);
    }

    private double validDpi(Double dpi) {
        return dpi == null || dpi < 250 || dpi > 1200 ? DEFAULT_DPI : dpi;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record MatchResult(
            boolean matched,
            double similarity,
            double threshold,
            String matricula,
            Servidor servidor) {
        public int percentual() {
            if (threshold <= 0) {
                return 0;
            }
            return (int) Math.max(0, Math.min(100, Math.round((similarity / threshold) * 100)));
        }
    }
}
