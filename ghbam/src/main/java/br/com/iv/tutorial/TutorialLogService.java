package br.com.iv.tutorial;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Log de tutoriais: quem clicou em "Como usar" e em qual tela. SQL nativo de proposito, como nos
 * favoritos do menu: sem @Entity, a validacao de schema do Hibernate nao depende desta tabela para subir.
 * A coluna "tipo" da tabela fica no padrao (PASSO_A_PASSO): o video de ajuda nao entra neste log.
 */
@ApplicationScoped
public class TutorialLogService {

    static final int LIMITE_LISTAGEM = 5000;

    @Inject
    EntityManager em;

    static String limitar(String texto, int maximo) {
        if (texto == null) return null;
        String limpo = texto.replaceAll("\\p{Cntrl}", " ").trim();
        if (limpo.isEmpty()) return null;
        return limpo.length() > maximo ? limpo.substring(0, maximo) : limpo;
    }

    /** Tela interna da aplicacao: nunca URL externa. */
    static boolean telaValida(String tela) {
        return tela == null || (tela.startsWith("/") && !tela.startsWith("//") && !tela.contains("://") && tela.length() <= 300);
    }

    /** Grava o clique; repetir o mesmo tutorial em menos de 1 minuto nao gera outra linha. */
    @Transactional
    public void registrar(String matricula, String nome, String unidade, String tutorial, String tela) {
        em.createNativeQuery(
                "INSERT INTO tutorial_visualizacao (matricula, nome, unidade, tutorial, tela) "
                        + "SELECT :m, :n, :u, :t, :tela WHERE NOT EXISTS ("
                        + " SELECT 1 FROM tutorial_visualizacao WHERE matricula = :m AND tutorial = :t"
                        + " AND visto_em > LOCALTIMESTAMP - INTERVAL '1 minute')")
                .setParameter("m", matricula)
                .setParameter("n", nome)
                .setParameter("u", unidade)
                .setParameter("t", tutorial)
                .setParameter("tela", tela)
                .executeUpdate();
    }

    /** Cliques da unidade no periodo (datas inclusivas), mais recentes primeiro. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listar(String unidade, LocalDate de, LocalDate ate) {
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT id, matricula, nome, unidade, tutorial, tela, visto_em FROM tutorial_visualizacao "
                        + "WHERE unidade = :u AND visto_em >= :de AND visto_em < :ate ORDER BY visto_em DESC")
                .setParameter("u", unidade)
                .setParameter("de", Timestamp.valueOf(de.atStartOfDay()))
                .setParameter("ate", Timestamp.valueOf(ate.plusDays(1).atStartOfDay()))
                .setMaxResults(LIMITE_LISTAGEM)
                .getResultList();
        List<Map<String, Object>> resultado = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", l[0]);
            item.put("matricula", l[1]);
            item.put("nome", l[2]);
            item.put("unidade", l[3]);
            item.put("tutorial", l[4]);
            item.put("tela", l[5]);
            Object visto = l[6];
            item.put("vistoEm", visto instanceof Timestamp ts ? ts.toLocalDateTime().toString() : visto == null ? null : visto.toString());
            resultado.add(item);
        }
        return resultado;
    }
}
