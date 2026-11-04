package br.com.iv.cme;

import br.com.iv.dto.LogDto;
import br.com.iv.service.LogService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cadastro de caixas/kits da CME por unidade, com a composicao (instrumentais e quantidades).
 * Excluir = desativar: o nome continua nos preparos e etiquetas ja registrados.
 */
@ApplicationScoped
public class CmeCaixaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    EntityManager em;

    @Inject
    LogService logService;

    public List<Map<String, Object>> listar(String unidade, boolean todos) {
        @SuppressWarnings("unchecked")
        List<Object[]> caixas = em.createNativeQuery(
                "SELECT idcaixa, nome, contem_implante, ativo, observacao FROM cme_caixa WHERE unidade = :u"
                        + (todos ? "" : " AND ativo = TRUE") + " ORDER BY ativo DESC, nome")
                .setParameter("u", unidade).getResultList();
        Map<Long, List<Map<String, Object>>> itens = new LinkedHashMap<>();
        if (!caixas.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> linhas = em.createNativeQuery(
                    "SELECT i.idcaixa, i.instrumento, i.quantidade FROM cme_caixa_item i JOIN cme_caixa c ON c.idcaixa = i.idcaixa "
                            + "WHERE c.unidade = :u ORDER BY i.idcaixa, i.ordem, i.iditem")
                    .setParameter("u", unidade).getResultList();
            for (Object[] l : linhas) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("instrumento", l[1]);
                it.put("quantidade", ((Number) l[2]).intValue());
                itens.computeIfAbsent(((Number) l[0]).longValue(), k -> new ArrayList<>()).add(it);
            }
        }
        List<Map<String, Object>> r = new ArrayList<>();
        for (Object[] c : caixas) {
            long id = ((Number) c[0]).longValue();
            List<Map<String, Object>> composicao = itens.getOrDefault(id, List.of());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idcaixa", id);
            m.put("nome", c[1]);
            m.put("contemImplante", Boolean.TRUE.equals(c[2]));
            m.put("ativo", Boolean.TRUE.equals(c[3]));
            m.put("observacao", c[4]);
            m.put("itens", composicao);
            m.put("totalPecas", composicao.stream().mapToInt(i -> (Integer) i.get("quantidade")).sum());
            r.add(m);
        }
        return r;
    }

    @Transactional
    public Map<String, Object> salvar(String unidade, Long idcaixa, Map<String, Object> p, String usuario) {
        String nome = texto(p, "nome");
        if (nome == null) throw new IllegalArgumentException("Informe o nome da caixa ou kit.");
        nome = nome.replaceAll("\\s+", " ").toUpperCase();
        if (nome.length() > 160) throw new IllegalArgumentException("Nome muito longo.");
        Query dup = em.createNativeQuery(
                "SELECT count(*) FROM cme_caixa WHERE unidade = :u AND nome = :n" + (idcaixa == null ? "" : " AND idcaixa <> :id"))
                .setParameter("u", unidade).setParameter("n", nome);
        if (idcaixa != null) dup.setParameter("id", idcaixa);
        if (((Number) dup.getSingleResult()).longValue() > 0) {
            throw new IllegalStateException("Já existe a caixa " + nome + " nesta unidade.");
        }

        List<Object[]> itens = itens(p);
        boolean implante = Boolean.parseBoolean(String.valueOf(p == null ? null : p.get("contemImplante")));
        String observacao = texto(p, "observacao") == null ? "" : texto(p, "observacao");
        Object ativoBruto = p == null ? null : p.get("ativo");

        Map<String, Object> antes = null;
        if (idcaixa == null) {
            Number id = (Number) em.createNativeQuery(
                    "INSERT INTO cme_caixa (unidade, nome, contem_implante, observacao, usuario_registro) "
                            + "VALUES (:u, :n, :imp, NULLIF(:obs, ''), :usr) RETURNING idcaixa")
                    .setParameter("u", unidade).setParameter("n", nome).setParameter("imp", implante)
                    .setParameter("obs", observacao).setParameter("usr", usuario).getSingleResult();
            idcaixa = id.longValue();
        } else {
            antes = buscar(unidade, idcaixa);
            boolean ativo = ativoBruto == null ? Boolean.TRUE.equals(antes.get("ativo")) : !"false".equalsIgnoreCase(String.valueOf(ativoBruto));
            em.createNativeQuery("UPDATE cme_caixa SET nome = :n, contem_implante = :imp, observacao = NULLIF(:obs, ''), ativo = :at, "
                            + "usuario_atualizacao = :usr, data_atualizacao = now() WHERE idcaixa = :id AND unidade = :u")
                    .setParameter("n", nome).setParameter("imp", implante).setParameter("obs", observacao)
                    .setParameter("at", ativo).setParameter("usr", usuario).setParameter("id", idcaixa).setParameter("u", unidade)
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM cme_caixa_item WHERE idcaixa = :id").setParameter("id", idcaixa).executeUpdate();
        }
        int ordem = 0;
        for (Object[] it : itens) {
            em.createNativeQuery("INSERT INTO cme_caixa_item (idcaixa, instrumento, quantidade, ordem) VALUES (:id, :i, :q, :o)")
                    .setParameter("id", idcaixa).setParameter("i", it[0]).setParameter("q", it[1]).setParameter("o", ordem++)
                    .executeUpdate();
        }

        Map<String, Object> depois = buscar(unidade, idcaixa);
        int pecas = (Integer) depois.get("totalPecas");
        if (antes == null) {
            log(unidade, usuario, "cadastrou a caixa " + nome + " com " + itens.size() + " instrumental(is), " + pecas + " peca(s)"
                    + (implante ? ", contem implante" : ""));
        } else if (!antes.get("ativo").equals(depois.get("ativo"))) {
            log(unidade, usuario, (Boolean.TRUE.equals(depois.get("ativo")) ? "reativou" : "excluiu") + " a caixa " + nome);
        } else {
            List<String> mudou = new ArrayList<>();
            if (!nome.equals(antes.get("nome"))) mudou.add("nome: " + antes.get("nome") + " -> " + nome);
            if (!antes.get("contemImplante").equals(depois.get("contemImplante"))) mudou.add("implante: " + (implante ? "sim" : "nao"));
            if (!antes.get("itens").equals(depois.get("itens"))) {
                mudou.add("composicao: " + antes.get("totalPecas") + " -> " + pecas + " peca(s)");
            }
            if (!mudou.isEmpty()) log(unidade, usuario, "alterou a caixa " + nome + ": " + String.join("; ", mudou));
        }
        return depois;
    }

    private Map<String, Object> buscar(String unidade, long idcaixa) {
        return listar(unidade, true).stream()
                .filter(c -> ((Long) c.get("idcaixa")) == idcaixa)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Caixa não encontrada nesta unidade."));
    }

    /** [instrumento, quantidade] validados; nomes repetidos somam a quantidade. */
    private static List<Object[]> itens(Map<String, Object> p) {
        Map<String, Integer> soma = new LinkedHashMap<>();
        Object bruto = p == null ? null : p.get("itens");
        if (bruto instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> l)) continue;
                Object nomeBruto = l.get("instrumento");
                String instrumento = nomeBruto == null ? "" : String.valueOf(nomeBruto).trim().replaceAll("\\s+", " ").toUpperCase();
                if (instrumento.isEmpty()) continue;
                if (instrumento.length() > 160) throw new IllegalArgumentException("Nome de instrumental muito longo.");
                int qtd;
                try {
                    qtd = Integer.parseInt(String.valueOf(l.get("quantidade")).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Quantidade inválida em " + instrumento + ".");
                }
                if (qtd <= 0 || qtd > 999) throw new IllegalArgumentException("Quantidade de " + instrumento + " deve ser de 1 a 999.");
                soma.merge(instrumento, qtd, Integer::sum);
            }
        }
        List<Object[]> r = new ArrayList<>();
        soma.forEach((k, v) -> r.add(new Object[] {k, v}));
        return r;
    }

    private void log(String unidade, String usuario, String texto) {
        LogDto dto = new LogDto();
        dto.setData(LocalDate.now());
        dto.setHora(LocalTime.now().format(HORA));
        dto.setEvento("CME: " + texto + " (matricula " + usuario + ")");
        dto.setUnidade(unidade == null ? null : unidade.length() > 20 ? unidade.substring(0, 20) : unidade);
        logService.salvarLog(dto);
    }

    private static String texto(Map<String, Object> p, String chave) {
        Object v = p == null ? null : p.get(chave);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
