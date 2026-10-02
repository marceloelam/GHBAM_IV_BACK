package br.com.iv.biofingertech;

import java.util.Base64;
import java.util.List;

import org.jboss.logging.Logger;

public final class BiometricMatcher {
    private static final Logger LOG = Logger.getLogger(BiometricMatcher.class);

    private BiometricMatcher() {}

    public static String matchOneToMany(String firToVerify, List<BiometriaDTO> templates) {
        if (firToVerify == null || firToVerify.isBlank() || templates == null || templates.isEmpty()) return null;

        // 1) Exact string match
        for (BiometriaDTO t : templates) {
            if (t.fir == null) continue;
            if (t.fir.equals(firToVerify)) {
                LOG.infof("Matcher: exact match matricula=%s", t.matricula);
                return t.matricula;
            }
        }

        // 2) Substring heuristic
        for (BiometriaDTO t : templates) {
            if (t.fir == null) continue;
            if (t.fir.contains(firToVerify) || firToVerify.contains(t.fir)) {
                LOG.infof("Matcher: substring match matricula=%s", t.matricula);
                return t.matricula;
            }
        }

        // 3) Byte-level similarity on Base64-decoded templates
        byte[] target = safeBase64Decode(firToVerify);
        if (target == null) return null;

        String bestMat = null;
        int bestMatchCount = -1;
        int bestLen = 1;

        for (BiometriaDTO t : templates) {
            if (t.fir == null) continue;
            byte[] cand = safeBase64Decode(t.fir);
            if (cand == null) continue;
            int match = byteMatchCount(target, cand);
            int len = Math.min(target.length, cand.length);
            if (match > bestMatchCount) {
                bestMatchCount = match;
                bestMat = t.matricula;
                bestLen = len;
            }
        }

        if (bestMatchCount > 0) {
            double ratio = bestMatchCount / (double) bestLen;
            LOG.infof("Matcher: best ratio=%.3f matricula=%s", ratio, bestMat);
            if (ratio >= 0.90) {
                return bestMat;
            }
        }

        return null;
    }

    private static byte[] safeBase64Decode(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int byteMatchCount(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        int cnt = 0;
        for (int i = 0; i < len; i++) {
            if (a[i] == b[i]) cnt++;
        }
        return cnt;
    }

    // Comparar dois FIRs diretamente; retorna true se considerados correspondentes.
    public static boolean matchTwoFirStrings(String firA, String firB) {
        if (firA == null || firB == null) return false;
        if (firA.isBlank() || firB.isBlank()) return false;

        if (firA.equals(firB)) return true;
        if (firA.contains(firB) || firB.contains(firA)) return true;

        byte[] a = safeBase64Decode(firA);
        byte[] b = safeBase64Decode(firB);
        if (a == null || b == null) return false;
        int match = byteMatchCount(a, b);
        int len = Math.min(a.length, b.length);
        if (len <= 0) return false;
        double ratio = match / (double) len;
        return ratio >= 0.90d;
    }
}
