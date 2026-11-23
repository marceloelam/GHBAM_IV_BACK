package br.com.iv.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * CIDs aceitos por procedimento (rl_procedimento_cid.txt da Tabela Unificada do
 * DATASUS), por competencia, na tabela sigtap_procedimento_cid (V263).
 *
 * Usado pelo motor de validacao (CID compativel em BPA-I) e pelas telas que lancam
 * procedimento (multiprofissional e odontologia). Procedimento sem nenhuma linha na
 * tabela = o SIGTAP nao restringe CID para ele: ninguem exige nada.
 */
@ApplicationScoped
public class SigtapCidService {

    private static final Logger LOG = Logger.getLogger(SigtapCidService.class);
    static final String ARQUIVO = "rl_procedimento_cid.txt";
    private static final Pattern PROC = Pattern.compile("\\d{10}");
    private static final Pattern CID = Pattern.compile("[A-Z]\\d{2}[0-9A-Z]?");
    private static final int LOTE = 1000;

    public record Linha(String procedimento, String cid, boolean principal) {
    }

    public record CidCompativel(String codigo, String descricao, boolean principal) {
    }

    @Inject
    EntityManager em;

    /** competencia|procedimento9 -> CIDs. Invalidado ao gravar/remover competencia. */
    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();
    private volatile List<String> competencias;

    // ---------------------------------------------------------------- importacao

    /**
     * Layout do rl_procedimento_cid.txt (DATASUS): CO_PROCEDIMENTO 1-10, CO_CID 11-14,
     * ST_PRINCIPAL 15, DT_COMPETENCIA 16-21.
     */
    public List<Linha> lerDoZip(byte[] zipBytes, String competencia) throws IOException {
        List<Linha> linhas = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.ISO_8859_1)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (ARQUIVO.equalsIgnoreCase(entry.getName())) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.ISO_8859_1));
                    String l;
                    while ((l = reader.readLine()) != null) {
                        Linha linha = interpretar(l, competencia);
                        if (linha != null) linhas.add(linha);
                    }
                }
                zip.closeEntry();
            }
        }
        return linhas;
    }

    static Linha interpretar(String l, String competencia) {
        if (l == null || l.length() < 21) return null;
        if (competencia != null && !competencia.equals(l.substring(15, 21))) return null;
        String proc = l.substring(0, 10).trim();
        String cid = l.substring(10, 14).trim().toUpperCase();
        if (!PROC.matcher(proc).matches() || !CID.matcher(cid).matches()) return null;
        return new Linha(proc, cid, "S".equalsIgnoreCase(l.substring(14, 15)));
    }

    /**
     * Substitui os CIDs da competencia. Transacao propria: falha aqui nao desfaz a
     * importacao dos procedimentos (o motor so deixa de checar CID, como hoje).
     */
    public int gravar(String competencia, List<Linha> linhas) {
        if (competencia == null || !competencia.matches("\\d{6}")) {
            throw new IllegalArgumentException("Competencia deve estar no formato AAAAMM.");
        }
        int total = QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("DELETE FROM sigtap_procedimento_cid WHERE competencia = :c")
                    .setParameter("c", competencia).executeUpdate();
            Set<String> vistos = new LinkedHashSet<>();
            StringBuilder sql = new StringBuilder();
            int n = 0;
            int gravados = 0;
            for (Linha l : linhas) {
                // valores ja validados por regex: seguros para o VALUES
                if (!PROC.matcher(l.procedimento()).matches() || !CID.matcher(l.cid()).matches()) continue;
                if (!vistos.add(l.procedimento() + l.cid())) continue;
                sql.append(n == 0 ? "" : ",").append("('").append(competencia).append("','")
                        .append(l.procedimento()).append("','").append(l.cid()).append("',")
                        .append(l.principal()).append(')');
                if (++n == LOTE) {
                    gravados += inserir(sql);
                    sql.setLength(0);
                    n = 0;
                }
            }
            if (n > 0) gravados += inserir(sql);
            return gravados;
        });
        invalidar();
        LOG.infof("SIGTAP %s: %d CIDs compativeis gravados", competencia, total);
        return total;
    }

    private int inserir(StringBuilder valores) {
        return em.createNativeQuery("INSERT INTO sigtap_procedimento_cid (competencia, co_procedimento, co_cid, principal) VALUES "
                + valores + " ON CONFLICT DO NOTHING").executeUpdate();
    }

    public long remover(String competencia) {
        long n = QuarkusTransaction.requiringNew().call(() -> (long) em
                .createNativeQuery("DELETE FROM sigtap_procedimento_cid WHERE competencia = :c")
                .setParameter("c", competencia).executeUpdate());
        invalidar();
        return n;
    }

    public void invalidar() {
        cache.clear();
        competencias = null;
    }

    // ---------------------------------------------------------------- consulta

    /** Competencia carregada mais recente que nao passa da pedida (ou a mais recente). */
    public String competenciaVigente(String competencia) {
        List<String> lista = competencias;
        if (lista == null) {
            @SuppressWarnings("unchecked")
            List<Object> r = em.createNativeQuery(
                    "SELECT DISTINCT competencia FROM sigtap_procedimento_cid ORDER BY competencia DESC").getResultList();
            lista = r.stream().map(String::valueOf).toList();
            competencias = lista;
        }
        if (lista.isEmpty()) return null;
        if (competencia != null && competencia.matches("\\d{6}")) {
            for (String c : lista) {
                if (c.compareTo(competencia) <= 0) return c;
            }
        }
        return lista.get(0);
    }

    /** Nove primeiros digitos (o DV nem sempre vem junto nas telas antigas). */
    static String chave(String procedimento) {
        String d = procedimento == null ? "" : procedimento.replaceAll("\\D", "");
        return d.length() >= 9 ? d.substring(0, 9) : "";
    }

    /** CIDs aceitos (sem ponto, maiusculo). Vazio = SIGTAP nao restringe ou tabela nao carregada. */
    public Set<String> cidsAceitos(String procedimento, String competencia) {
        String p9 = chave(procedimento);
        String cmp = competenciaVigente(competencia);
        if (p9.isEmpty() || cmp == null) return Set.of();
        return cache.computeIfAbsent(cmp + "|" + p9, k -> {
            @SuppressWarnings("unchecked")
            List<Object> r = em.createNativeQuery("SELECT co_cid FROM sigtap_procedimento_cid "
                    + "WHERE competencia = :c AND co_procedimento LIKE :p")
                    .setParameter("c", cmp).setParameter("p", p9 + "%").getResultList();
            Set<String> s = new LinkedHashSet<>();
            r.forEach(o -> s.add(String.valueOf(o)));
            return Set.copyOf(s);
        });
    }

    public boolean exigeCid(String procedimento, String competencia) {
        return !cidsAceitos(procedimento, competencia).isEmpty();
    }

    public static String normalizarCid(String cid) {
        return cid == null ? "" : cid.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /** Aceito se o CID esta na lista (ou se a lista so traz a categoria de 3 caracteres). */
    public boolean compativel(String procedimento, String competencia, String cid) {
        Set<String> aceitos = cidsAceitos(procedimento, competencia);
        if (aceitos.isEmpty()) return true;
        String c = normalizarCid(cid);
        return !c.isEmpty() && (aceitos.contains(c) || (c.length() == 4 && aceitos.contains(c.substring(0, 3))));
    }

    /**
     * CIDs ja registrados no episodio (laudo de AIH, internacao, resumo de alta) que o
     * SIGTAP aceita para o procedimento - sugeridos primeiro na tela.
     */
    public List<CidCompativel> doEpisodioCompativeis(String procedimento, Integer bam) {
        if (bam == null || bam <= 0 || !exigeCid(procedimento, null)) return List.of();
        @SuppressWarnings("unchecked")
        List<Object> r = em.createNativeQuery(
                "SELECT cidprincipal FROM laudoautorizacao WHERE bam = :b "
                + "UNION ALL SELECT cid FROM internacao WHERE bam = :bt "
                + "UNION ALL SELECT cid2 FROM resumodealta WHERE bam = :bt "
                + "UNION ALL SELECT cid FROM resumodealta WHERE bam = :bt")
                .setParameter("b", bam).setParameter("bt", String.valueOf(bam)).getResultList();
        Set<String> vistos = new LinkedHashSet<>();
        List<CidCompativel> lista = new ArrayList<>();
        for (Object o : r) {
            String c = normalizarCid(o == null ? "" : String.valueOf(o));
            if (c.isEmpty() || !vistos.add(c) || !compativel(procedimento, null, c)) continue;
            List<CidCompativel> achado = listar(procedimento, c, 1);
            lista.add(achado.isEmpty() ? new CidCompativel(c, "", false) : achado.get(0));
        }
        return lista;
    }

    /** Lista para as telas: filtro por codigo ou descricao, principais primeiro. */
    public List<CidCompativel> listar(String procedimento, String filtro, int limite) {
        String p9 = chave(procedimento);
        String cmp = competenciaVigente(null);
        if (p9.isEmpty() || cmp == null) return List.of();
        String f = filtro == null ? "" : filtro.trim();
        @SuppressWarnings("unchecked")
        List<Object[]> r = em.createNativeQuery("SELECT p.co_cid, coalesce(max(c.cd_descr), ''), bool_or(p.principal) "
                + "FROM sigtap_procedimento_cid p LEFT JOIN s_cid c ON upper(trim(c.cd_cod)) = p.co_cid "
                + "WHERE p.competencia = :c AND p.co_procedimento LIKE :p "
                + "AND (:f = '' OR p.co_cid LIKE :fc OR upper(coalesce(c.cd_descr, '')) LIKE upper(:fd)) "
                + "GROUP BY p.co_cid ORDER BY bool_or(p.principal) DESC, p.co_cid LIMIT :l")
                .setParameter("c", cmp).setParameter("p", p9 + "%").setParameter("f", f)
                .setParameter("fc", normalizarCid(f) + "%").setParameter("fd", "%" + f + "%")
                .setParameter("l", Math.max(1, Math.min(limite, 200))).getResultList();
        List<CidCompativel> lista = new ArrayList<>();
        for (Object[] o : r) {
            String desc = String.valueOf(o[1]).replaceFirst("^\\S+\\s+", "").trim();
            lista.add(new CidCompativel(String.valueOf(o[0]), desc, Boolean.TRUE.equals(o[2])));
        }
        return lista;
    }
}
