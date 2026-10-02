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
