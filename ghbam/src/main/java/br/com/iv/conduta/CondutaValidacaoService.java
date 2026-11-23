package br.com.iv.conduta;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validação das condutas clínicas (tabela conduta_validacao, V267).
 * Só valida médico com CRM no cadastro E administrativo (kadm) ou coordenador de setor
 * (pcoordenadorsetor). Super usuário sem CRM não valida: a validação é assinatura de médico.
 */
@ApplicationScoped
public class CondutaValidacaoService {

    private static final Pattern UUID_VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final Pattern HASH_VALIDO = Pattern.compile("[0-9a-f]{64}");

    @Inject
    EntityManager em;

    public static boolean podeValidar(AuthenticatedUser u) {
        if (u == null || u.crm() == null || u.crm().isBlank()) return false;
        return u.hasPermission("kadm") || u.hasPermission("pcoordenadorsetor");
    }

    public static String motivoSemPermissao(AuthenticatedUser u) {
        if (u == null || u.crm() == null || u.crm().isBlank()) {
            return "A validação exige médico com CRM no cadastro do servidor.";
        }
        return "A validação exige permissão de administrativo ou de coordenador de setor.";
    }

    public static boolean uuidValido(String uuid) {
        return uuid != null && UUID_VALIDO.matcher(uuid).matches();
    }

    public static boolean hashValido(String hash) {
        return hash != null && HASH_VALIDO.matcher(hash).matches();
    }

    /** Validação ativa mais recente de cada conduta. */
    public List<Map<String, Object>> ativas() {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                "select distinct on (conduta_uuid) conduta_uuid, hash_conteudo, nome, crm, validado_em "
                        + "from conduta_validacao where revogado_em is null "
                        + "order by conduta_uuid, validado_em desc")
                .getResultList();
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Object[] l : linhas) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", l[0]);
            m.put("hash", l[1]);
            m.put("nome", l[2]);
            m.put("crm", l[3]);
            m.put("validadoEm", l[4] == null ? null : l[4].toString());
            saida.add(m);
        }
        return saida;
    }

    /** Revoga a validação anterior (se houver) e registra a nova, tudo na mesma transação. */
    @Transactional
    public void validar(AuthenticatedUser u, String uuid, String hash, String titulo) {
        revogarAtivas(uuid, u.matricula().trim());
        em.createNativeQuery(
                "insert into conduta_validacao (conduta_uuid, titulo, hash_conteudo, matricula, nome, crm) "
                        + "values (?1, ?2, ?3, ?4, ?5, ?6)")
                .setParameter(1, uuid)
                .setParameter(2, limitar(titulo, 200))
                .setParameter(3, hash)
                .setParameter(4, u.matricula().trim())
                .setParameter(5, limitar(u.nome(), 200))
                .setParameter(6, limitar(u.crm().trim(), 40))
                .executeUpdate();
    }

    @Transactional
    public int revogar(AuthenticatedUser u, String uuid) {
        return revogarAtivas(uuid, u.matricula().trim());
    }

    private int revogarAtivas(String uuid, String matricula) {
        return em.createNativeQuery(
                "update conduta_validacao set revogado_em = localtimestamp, revogado_por = ?2 "
                        + "where conduta_uuid = ?1 and revogado_em is null")
                .setParameter(1, uuid)
                .setParameter(2, matricula)
                .executeUpdate();
    }

    static String limitar(String valor, int max) {
        if (valor == null) return null;
        String v = valor.replaceAll("\\s+", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
