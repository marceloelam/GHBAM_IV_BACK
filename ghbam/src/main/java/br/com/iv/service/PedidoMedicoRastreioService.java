package br.com.iv.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Rastreio do pedido medico (pedidomedico2): o medico pede (medico, crm, dt, hr) e o setor
 * executa (executado_em, executado_por). A execucao e carimbada pelo servidor com o usuario
 * da sessao, item a item ou na finalizacao em lote do BAM; nunca com dado vindo do cliente.
 */
@ApplicationScoped
public class PedidoMedicoRastreioService {

    @Inject
    EntityManager em;

    /** Mesmo agrupamento de locais da finalizacao (PedidoMedico2Repository). */
    static List<String> locais(String local) {
        String l = local == null ? "" : local.trim().toUpperCase();
        if (l.equals("MEDICACAO") || l.equals("MEDICAMENTO")) return List.of("MEDICACAO", "MEDICAMENTO");
        if (l.equals("MEDICACAO2") || l.equals("MEDICAMENTO2")) return List.of("MEDICACAO2", "MEDICAMENTO2");
        return List.of(l);
    }

    static String nome(AuthenticatedUser u) {
        if (u == null) return "Usuario nao identificado";
        String nome = u.nome() == null ? "" : u.nome().trim();
        return nome.isEmpty() ? "Matricula " + u.matricula() : nome;
    }

    /** Carimba os itens ainda pendentes do BAM/local antes da finalizacao em lote marcar efetuado. */
    @Transactional
    public int carimbarPendentes(int bam, String local, AuthenticatedUser u) {
        return em.createNativeQuery("update pedidomedico2 set executado_em = now(), executado_por = :nome,"
                + " executado_por_matricula = :mat where bam = :bam and efetuado = false and local in (:locais)"
                + " and executado_em is null")
                .setParameter("nome", nome(u))
                .setParameter("mat", u == null ? null : u.matricula())
                .setParameter("bam", bam)
                .setParameter("locais", locais(local))
                .executeUpdate();
    }

    /** Executa um item do pedido. Falha se ja estava executado (evita sobrescrever o rastreio). */
    @Transactional
    public Map<String, Object> executarItem(int idpedidomedico, AuthenticatedUser u) {
        if (u == null || u.matricula() == null || u.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        int n = em.createNativeQuery("update pedidomedico2 set efetuado = true, executado_em = now(),"
                + " executado_por = :nome, executado_por_matricula = :mat"
                + " where idpedidomedico = :id and coalesce(efetuado, false) = false")
                .setParameter("nome", nome(u))
                .setParameter("mat", u.matricula())
                .setParameter("id", idpedidomedico)
                .executeUpdate();
        if (n == 0) {
            throw new IllegalStateException("Item nao encontrado ou ja executado.");
        }
        return Map.of("idpedidomedico", idpedidomedico, "executado", true);
    }

    /** Linha do tempo do pedido medico do BAM: quem pediu e quando, quem executou e quando. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> rastreio(int bam, String local) {
        String filtro = local == null || local.isBlank() ? "" : " and local in (:locais)";
        var q = em.createNativeQuery("select idpedidomedico, pedido, local, setor, medico, crm, dt, hr,"
                + " coalesce(efetuado, false), executado_em, executado_por, executado_por_matricula, obs"
                + " from pedidomedico2 where bam = :bam" + filtro + " order by idpedidomedico")
                .setParameter("bam", bam);
        if (!filtro.isEmpty()) q.setParameter("locais", locais(local));
        List<Map<String, Object>> r = new ArrayList<>();
        for (Object[] l : (List<Object[]>) q.getResultList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idpedidomedico", ((Number) l[0]).intValue());
            m.put("pedido", l[1]);
            m.put("local", l[2]);
            m.put("setor", l[3]);
            m.put("medico", l[4]);
            m.put("crm", l[5]);
            m.put("pedidoData", l[6]);
            m.put("pedidoHora", l[7]);
            m.put("efetuado", Boolean.TRUE.equals(l[8]));
            m.put("executadoEm", l[9] instanceof Timestamp t ? t.toLocalDateTime().toString() : null);
            m.put("executadoPor", l[10]);
            m.put("executadoPorMatricula", l[11]);
            // obs = o que o medico digitou (medicamentos); pedido = procedimento SIGTAP do faturamento.
            m.put("prescricao", l[12]);
            r.add(m);
        }
        return r;
    }
}
