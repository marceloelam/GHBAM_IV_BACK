package br.com.iv.protocolo;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aprovacao dos protocolos em fluxograma (V246). Historico append-only: a situacao vigente de
 * um protocolo/versao e a decisao mais recente. Aprova so quem tem a opcao "Comissão de
 * protocolos" no cadastro de servidores E perfil de medico, enfermagem ou multi (kadm so gerencia).
 */
@ApplicationScoped
public class ProtocoloAprovacaoService {

    static final Set<String> DECISOES = Set.of("APROVADO", "REPROVADO", "REVOGADO");

    @Inject
    EntityManager em;

    /** Opcao do cadastro de servidores (/servidores), coluna servidor2.pprotocoloaprovacao (V250). */
    static final String PERMISSAO_COMISSAO = "pprotocoloaprovacao";

    /** So profissional assistencial pode compor a comissao: medico, enfermagem ou multidisciplinar. */
    static final List<String> PERFIS_CLINICOS = List.of("kmedico", "kenfermagem", "kfisioterapia", "kassistencia",
            "knutricao", "kfonoaudiologia", "kpsicologia");

    /**
     * So a comissao aprova/edita: opcao "Comissão de protocolos" ligada E perfil clinico.
     * Administracao (kadm) NAO aprova por ser administracao — so gerencia quem esta na comissao
     * (decisao de 10/11/2026: aprovar conteudo clinico e de profissional de saude).
     */
    public boolean podeAprovar(AuthenticatedUser user) {
        if (user == null) return false;
        return user.hasPermission(PERMISSAO_COMISSAO) && user.hasAnyPermission(PERFIS_CLINICOS);
    }

    // ---- Comissao: cartao no cadastro de servidores (Administracao do sistema), so kadm ----

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(ProtocoloAprovacaoService.class);

    /** Servidor tem perfil assistencial (medico, enfermagem ou multidisciplinar) no cadastro? */
    @SuppressWarnings("unchecked")
    boolean perfilClinico(String matricula) {
        List<Object[]> l = em.createNativeQuery(
                "SELECT COALESCE(medico,false), COALESCE(enfermagem,false), COALESCE(fisioterapia,false), "
                        + "COALESCE(assistencia,false), COALESCE(nutricao,false), COALESCE(fonoaudiologia,false), "
                        + "COALESCE(psicologia,false) FROM servidor WHERE matricula = :m LIMIT 1")
                .setParameter("m", matricula)
                .getResultList();
        if (l.isEmpty()) return false;
        for (Object v : l.get(0)) if (Boolean.TRUE.equals(v)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> situacaoComissao(String matricula) {
        String m = ProtocoloExecucaoService.limitar(matricula, 80);
        if (m == null) throw new IllegalArgumentException("Informe a matrícula.");
        List<Object> s2 = em.createNativeQuery("SELECT COALESCE(pprotocoloaprovacao,false) FROM servidor2 WHERE matricula = :m")
                .setParameter("m", m)
                .getResultList();
        boolean membro = s2.stream().anyMatch(Boolean.TRUE::equals);
        return Map.of("perfilClinico", perfilClinico(m), "membro", membro, "temAcessos", !s2.isEmpty());
    }

    @Transactional
    public void definirComissao(String matricula, boolean membro, AuthenticatedUser autor) {
        String m = ProtocoloExecucaoService.limitar(matricula, 80);
        if (m == null) throw new IllegalArgumentException("Informe a matrícula.");
        if (membro && !perfilClinico(m)) {
            throw new IllegalArgumentException("Só médico, enfermagem ou equipe multidisciplinar pode compor a comissão de protocolos.");
        }
        int n = em.createNativeQuery("UPDATE servidor2 SET pprotocoloaprovacao = :v WHERE matricula = :m")
                .setParameter("v", membro)
                .setParameter("m", m)
                .executeUpdate();
        if (n == 0) throw new IllegalArgumentException("Servidor sem cadastro de acessos. Salve os acessos dele primeiro.");
        LOG.infof("Comissao de protocolos: matricula %s %s por %s (%s)", m, membro ? "INCLUIDA" : "RETIRADA",
                autor.matricula(), autor.nome());
    }

    private static Map<String, Object> linha(Object[] l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ((Number) l[0]).longValue());
        m.put("protocoloId", l[1]);
        m.put("versao", l[2]);
        m.put("decisao", l[3]);
        m.put("comentario", l[4]);
        m.put("nome", l[5]);
        m.put("decididoEm", l[6] instanceof Timestamp t ? t.toLocalDateTime().toString() : l[6]);
        m.put("matricula", l[7]);
        m.put("unidade", l[8]);
        m.put("registro", l[9]);
        return m;
    }

    /** Decisao vigente (mais recente) de cada protocolo/versao. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> vigentes() {
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT DISTINCT ON (protocolo_id, protocolo_versao) id, protocolo_id, protocolo_versao, decisao, "
                        + "comentario, nome, decidido_em, matricula, unidade, registro FROM protocolo_fluxo_aprovacao "
                        + "ORDER BY protocolo_id, protocolo_versao, decidido_em DESC, id DESC")
                .getResultList();
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Object[] l : linhas) saida.add(linha(l));
        return saida;
    }

    /** Historico completo de um protocolo (todas as versoes), mais recente primeiro. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> historico(String protocoloId) {
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT id, protocolo_id, protocolo_versao, decisao, comentario, nome, decidido_em, matricula, unidade, registro "
                        + "FROM protocolo_fluxo_aprovacao WHERE protocolo_id = :p ORDER BY decidido_em DESC, id DESC LIMIT 100")
                .setParameter("p", protocoloId)
                .getResultList();
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Object[] l : linhas) saida.add(linha(l));
        return saida;
    }

    @Transactional
    public void decidir(String protocoloId, String versao, String decisao, String comentario, String unidade,
                        AuthenticatedUser user) {
        String d = decisao == null ? "" : decisao.trim().toUpperCase();
        if (!DECISOES.contains(d)) throw new IllegalArgumentException("Decisão inválida.");
        String p = ProtocoloExecucaoService.limitar(protocoloId, 80);
        String v = ProtocoloExecucaoService.limitar(versao, 20);
        if (p == null || v == null) throw new IllegalArgumentException("Informe protocolo e versão.");
        String c = ProtocoloExecucaoService.limitar(comentario, 4000);
        // Toda decisao (inclusive aprovar) exige justificativa, para a rastreabilidade da governanca.
        if (c == null || c.length() < 5) {
            throw new IllegalArgumentException("Informe a justificativa da decisão (mínimo 5 caracteres).");
        }
        em.createNativeQuery(
                "INSERT INTO protocolo_fluxo_aprovacao (protocolo_id, protocolo_versao, decisao, comentario, matricula, nome, unidade, registro) "
                        + "VALUES (:p, :v, :d, :c, :m, CAST(:n AS VARCHAR), CAST(:u AS VARCHAR), CAST(:r AS VARCHAR))")
                .setParameter("r", ProtocoloExecucaoService.registroProfissional(user))
                .setParameter("p", p)
                .setParameter("v", v)
                .setParameter("d", d)
                .setParameter("c", c)
                .setParameter("m", user.matricula().trim())
                .setParameter("n", ProtocoloExecucaoService.limitar(user.nome(), 200))
                .setParameter("u", ProtocoloExecucaoService.limitar(unidade, 80))
                .executeUpdate();
    }
}
