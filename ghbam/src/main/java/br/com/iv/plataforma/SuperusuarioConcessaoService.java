package br.com.iv.plataforma;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Quem pode conceder ou retirar Super usuario.
 * <ul>
 * <li>Responsavel da plataforma: matricula em {@code ghbam.seguranca.superusuario.responsavel}. Sem a chave,
 * ninguem concede.</li>
 * <li>Designados: tabela superusuario_concedente (V260). So o responsavel designa ou cancela.</li>
 * </ul>
 * Toda concessao, designacao ou cancelamento exige confirmacao facial com prova de vida de quem esta logado:
 * a confirmacao vira um comprovante de uso unico, preso a quem confirmou, a finalidade e a matricula alvo.
 */
@ApplicationScoped
public class SuperusuarioConcessaoService {

    private static final Logger LOG = Logger.getLogger(SuperusuarioConcessaoService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    static final long VALIDADE_SEGUNDOS = 600;
    private static final int MAX_COMPROVANTES = 1000;

    public static final String CONCEDER = "CONCEDER";
    public static final String DESIGNAR = "DESIGNAR";

    /** Recusa de concessao: vira HTTP 403. */
    public static class NegadoException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NegadoException(String mensagem) {
            super(mensagem);
        }
    }

    record Comprovante(String matricula, String finalidade, String alvo, Instant expiraEm) {
    }

    private final Map<String, Comprovante> comprovantes = new ConcurrentHashMap<>();

    @Inject
    EntityManager em;

    static String normalizar(String matricula) {
        return matricula == null ? "" : matricula.trim();
    }

    static String finalidade(String valor) {
        String f = valor == null ? "" : valor.trim().toUpperCase();
        if (!CONCEDER.equals(f) && !DESIGNAR.equals(f)) {
            throw new IllegalArgumentException("Finalidade invalida.");
        }
        return f;
    }

    @Inject
    io.agroal.api.AgroalDataSource dataSource;

    /**
     * Ao subir o backend, o responsavel da plataforma (configurado no servidor) volta a ser Super usuario no
     * banco principal - ex.: depois de restaurar um backup. So liga, nunca desliga ninguem; sem a chave ou sem
     * o cadastro em servidor2, nao faz nada. Desligar: ghbam.seguranca.superusuario.responsavel.garantir-ao-subir=false.
     */
    void garantirResponsavelAoSubir(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent evento) {
        boolean garantir = ConfigProvider.getConfig()
                .getOptionalValue("ghbam.seguranca.superusuario.responsavel.garantir-ao-subir", Boolean.class)
                .orElse(true);
        String r = responsavel();
        if (!garantir || r.isEmpty()) {
            return;
        }
        try (java.sql.Connection c = dataSource.getConnection();
                java.sql.PreparedStatement ps = c.prepareStatement(
                        "UPDATE servidor2 SET superusuario = true WHERE trim(matricula) = ? "
                                + "AND superusuario IS DISTINCT FROM true")) {
            ps.setString(1, r);
            int n = ps.executeUpdate();
            if (n > 0) {
                LOG.warn("[SUPERUSUARIO] responsavel da plataforma " + r
                        + " estava sem Super usuario no banco principal; religado ao subir o backend.");
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOG.warn("[SUPERUSUARIO] nao foi possivel garantir o responsavel ao subir: " + e.getMessage());
        }
    }

    String responsavel() {
        return normalizar(ConfigProvider.getConfig()
                .getOptionalValue("ghbam.seguranca.superusuario.responsavel", String.class).orElse(""));
    }

    public boolean ehResponsavel(String matricula) {
        String m = normalizar(matricula);
        String r = responsavel();
        return !m.isEmpty() && !r.isEmpty() && m.equals(r);
    }

    public boolean ehDesignado(String matricula) {
        String m = normalizar(matricula);
        if (m.isEmpty()) {
            return false;
        }
        Number n = (Number) em.createNativeQuery(
                "SELECT count(*) FROM superusuario_concedente WHERE matricula = ?1 AND ativo")
                .setParameter(1, m).getSingleResult();
        return n.longValue() > 0;
    }

    public boolean podeConceder(String matricula) {
        return ehResponsavel(matricula) || ehDesignado(matricula);
    }

    /** Designado com a marca pode_designar (V261): designa e cancela quem concede. */
    public boolean temPoderDesignar(String matricula) {
        String m = normalizar(matricula);
        if (m.isEmpty()) {
            return false;
        }
        Number n = (Number) em.createNativeQuery(
                "SELECT count(*) FROM superusuario_concedente WHERE matricula = ?1 AND ativo AND pode_designar")
                .setParameter(1, m).getSingleResult();
        return n.longValue() > 0;
    }

    public boolean podeDesignar(String matricula) {
        return ehResponsavel(matricula) || temPoderDesignar(matricula);
    }

    /**
     * Regra de designar/cancelar. Devolve o motivo da recusa ou null.
     * So o responsavel da poder de designar a outro; quem recebeu esse poder designa e cancela apenas quem
     * concede, nunca o responsavel nem outro que tambem designa.
     */
    static String recusaDesignacao(boolean quemResponsavel, boolean quemPodeDesignar, boolean alvoResponsavel,
            boolean alvoPodeDesignar, boolean darPoderDesignar) {
        if (!quemResponsavel && !quemPodeDesignar) {
            return "Somente o responsavel pela plataforma, ou quem ele autorizar, designa ou cancela quem concede Super usuario.";
        }
        if (alvoResponsavel) {
            return "O responsavel pela plataforma nao pode ser designado nem cancelado.";
        }
        if (!quemResponsavel && (alvoPodeDesignar || darPoderDesignar)) {
            return "Somente o responsavel pela plataforma da ou retira o poder de designar.";
        }
        return null;
    }

    /** Confere se quem esta logado pode iniciar a confirmacao facial para esta finalidade. */
    public void exigirPapel(String matricula, String finalidade) {
        boolean designar = DESIGNAR.equals(finalidade(finalidade));
        boolean ok = designar ? podeDesignar(matricula) : podeConceder(matricula);
        if (!ok) {
            throw new NegadoException(designar
                    ? "Somente o responsavel pela plataforma, ou quem ele autorizar, designa ou cancela quem concede Super usuario."
                    : "Voce nao esta autorizado a conceder ou retirar Super usuario.");
        }
    }

    /** Emite o comprovante depois da confirmacao facial aprovada. */
    public String emitir(String matricula, String finalidade, String alvo) {
        Instant agora = Instant.now();
        comprovantes.values().removeIf(c -> agora.isAfter(c.expiraEm()));
        if (comprovantes.size() >= MAX_COMPROVANTES) {
            throw new IllegalStateException("Muitas confirmacoes pendentes. Tente novamente em instantes.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String f = finalidade(finalidade);
        comprovantes.put(id, new Comprovante(normalizar(matricula), f, normalizar(alvo),
                agora.plusSeconds(VALIDADE_SEGUNDOS)));
        LOG.warnf("[SUPERUSUARIO] confirmacao facial aprovada matricula=%s finalidade=%s alvo=%s",
                normalizar(matricula), f, normalizar(alvo));
        return id;
    }

    /** Consome o comprovante (uso unico). Falha se nao bater quem, finalidade ou alvo. */
    public void consumir(String comprovante, String matricula, String finalidade, String alvo) {
        Comprovante c = comprovante == null || comprovante.isBlank() ? null : comprovantes.remove(comprovante.trim());
        if (c == null || Instant.now().isAfter(c.expiraEm()) || !c.matricula().equals(normalizar(matricula))
                || !c.finalidade().equals(finalidade(finalidade)) || !c.alvo().equals(normalizar(alvo))) {
            throw new NegadoException("Alterar Super usuario exige confirmacao facial de quem esta concedendo. "
                    + "Confirme o rosto de novo e salve em seguida.");
        }
    }

    /** Usado ao gravar servidor2 quando o Super usuario muda (ligar ou desligar). */
    public void exigirConcessao(AuthenticatedUser quem, String comprovante, String alvo) {
        String m = quem == null ? "" : normalizar(quem.matricula());
        if (!podeConceder(m)) {
            throw new NegadoException("Somente o responsavel pela plataforma, ou quem ele designar, "
                    + "concede ou retira Super usuario.");
        }
        consumir(comprovante, m, CONCEDER, alvo);
    }

    public void registrarAlteracao(AuthenticatedUser quem, String alvo, boolean ligado) {
        LOG.warnf("[SUPERUSUARIO] %s matricula=%s por=%s", ligado ? "concedido" : "retirado", normalizar(alvo),
                quem == null ? "" : normalizar(quem.matricula()));
    }

    public List<Map<String, Object>> designados() {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("SELECT c.matricula, coalesce(s.nome, ''), c.designado_por, "
                + "c.criado_em, c.pode_designar FROM superusuario_concedente c "
                + "LEFT JOIN servidor s ON s.matricula = c.matricula WHERE c.ativo ORDER BY c.criado_em")
                .getResultList();
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object[] l : linhas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("matricula", l[0]);
            item.put("nome", l[1]);
            item.put("designadoPor", l[2]);
            item.put("criadoEm", l[3] == null ? null : l[3].toString());
            item.put("podeDesignar", Boolean.TRUE.equals(l[4]));
            lista.add(item);
        }
        return lista;
    }

    private void exigirDesignacao(String m, String a, boolean darPoderDesignar) {
        String recusa = recusaDesignacao(ehResponsavel(m), temPoderDesignar(m), ehResponsavel(a),
                temPoderDesignar(a), darPoderDesignar);
        if (recusa != null) {
            throw new NegadoException(recusa);
        }
    }

    /** Designa (ou atualiza) quem concede. podeDesignar=true tambem o deixa designar e cancelar. */
    @Transactional
    public void designar(AuthenticatedUser quem, String comprovante, String alvo, boolean podeDesignar) {
        String m = quem == null ? "" : normalizar(quem.matricula());
        String a = normalizar(alvo);
        if (!a.matches("\\d{1,30}")) {
            throw new IllegalArgumentException("Matricula invalida.");
        }
        if (a.equals(m)) {
            throw new IllegalArgumentException("Voce nao pode designar a si mesmo.");
        }
        exigirDesignacao(m, a, podeDesignar);
        consumir(comprovante, m, DESIGNAR, a);
        int n = em.createNativeQuery("UPDATE superusuario_concedente SET pode_designar = ?2 WHERE matricula = ?1 AND ativo")
                .setParameter(1, a).setParameter(2, podeDesignar).executeUpdate();
        if (n == 0) {
            em.createNativeQuery("INSERT INTO superusuario_concedente (matricula, designado_por, pode_designar) "
                    + "VALUES (?1, ?2, ?3)").setParameter(1, a).setParameter(2, m).setParameter(3, podeDesignar)
                    .executeUpdate();
        }
        LOG.warnf("[SUPERUSUARIO] designado matricula=%s podeDesignar=%s por=%s", a, podeDesignar, m);
    }

    @Transactional
    public void cancelar(AuthenticatedUser quem, String comprovante, String alvo) {
        String m = quem == null ? "" : normalizar(quem.matricula());
        String a = normalizar(alvo);
        exigirDesignacao(m, a, false);
        consumir(comprovante, m, DESIGNAR, a);
        em.createNativeQuery("UPDATE superusuario_concedente SET ativo = FALSE, cancelado_por = ?2, "
                + "cancelado_em = now() WHERE matricula = ?1 AND ativo")
                .setParameter(1, a).setParameter(2, m).executeUpdate();
        LOG.warnf("[SUPERUSUARIO] designacao cancelada matricula=%s por=%s", a, m);
    }
}
