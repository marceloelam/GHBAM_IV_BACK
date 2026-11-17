package br.com.iv.plataforma;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.mindrot.jbcrypt.BCrypt;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.util.UnidadeUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Credenciais de sistemas integradores (tabela api_cliente, V259). O sistema troca client_id + segredo
 * por um JWT (tipo=cliente) e passa pelas mesmas regras de RBAC e de unidade que um profissional.
 */
@ApplicationScoped
public class ApiClienteService {

    /** Prefixo da "matricula" do cliente: nunca colide com matricula de servidor (so digitos). */
    public static final String PREFIXO = "cliente:";
    public static final String CARGO = "INTEGRACAO";

    // Cliente de integracao nunca recebe poderes de plataforma.
    static final Set<String> PERMISSOES_PROIBIDAS = Set.of("ksuperusuario", "superusuario");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CACHE_MS = Duration.ofSeconds(60).toMillis();

    public record Cliente(String clientId, String nome, String unidade, List<String> permissoes, boolean ativo) {
    }

    public record Criado(String clientId, String clientSecret) {
    }

    private record EmCache(Cliente cliente, long lidoEm) {
    }

    private final Map<String, EmCache> cache = new ConcurrentHashMap<>();

    @Inject
    EntityManager em;

    /** Lista de permissoes (csv) limpa: minusculas, sem repetidas, sem as de plataforma. */
    static List<String> normalizarPermissoes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split("[,;\\s]+"))
                .map(p -> p.trim().toLowerCase(Locale.ROOT))
                // Permissoes do sistema sao k* (perfil) ou p* (tela): o resto e' descartado.
                .filter(p -> p.matches("[kp][a-z0-9_]{2,59}") && !PERMISSOES_PROIBIDAS.contains(p))
                .distinct()
                .collect(Collectors.toList());
    }

    /** Usuario da requisicao para um cliente ativo (mesmo formato de um profissional). */
    static AuthenticatedUser paraUsuario(Cliente c) {
        Map<String, Boolean> perms = new LinkedHashMap<>();
        c.permissoes().forEach(p -> perms.put(p, true));
        return new AuthenticatedUser("", PREFIXO + c.clientId(), c.nome(), CARGO, c.unidade(), "", "", "", "", "",
                "", "", false, perms);
    }

    static String gerarSegredo() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String gerarClientId() {
        byte[] b = new byte[10];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder("cli_");
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    @Transactional
    public Criado criar(String nome, String unidade, String permissoesCsv, String criadoPor) {
        String n = nome == null ? "" : nome.trim();
        String u = UnidadeUtil.normalizar(unidade);
        if (n.isBlank() || u.isBlank()) {
            throw new IllegalArgumentException("Nome e unidade do sistema integrador sao obrigatorios.");
        }
        List<String> perms = normalizarPermissoes(permissoesCsv);
        if (perms.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma permissao (ex.: kenfermagem).");
        }
        String clientId = gerarClientId();
        String segredo = gerarSegredo();
        em.createNativeQuery("INSERT INTO api_cliente (client_id, segredo_hash, nome, unidade, permissoes, criado_por)"
                + " VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
                .setParameter(1, clientId)
                .setParameter(2, BCrypt.hashpw(segredo, BCrypt.gensalt()))
                .setParameter(3, n.length() > 120 ? n.substring(0, 120) : n)
                .setParameter(4, u)
                .setParameter(5, String.join(",", perms))
                .setParameter(6, criadoPor)
                .executeUpdate();
        return new Criado(clientId, segredo);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listar() {
        List<Object[]> linhas = em.createNativeQuery("SELECT client_id, nome, unidade, permissoes, ativo, criado_por,"
                + " criado_em, ultimo_uso FROM api_cliente ORDER BY criado_em DESC").getResultList();
        return linhas.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientId", l[0]);
            m.put("nome", l[1]);
            m.put("unidade", l[2]);
            m.put("permissoes", l[3]);
            m.put("ativo", l[4]);
            m.put("criadoPor", l[5]);
            m.put("criadoEm", l[6] == null ? null : l[6].toString());
            m.put("ultimoUso", l[7] == null ? null : l[7].toString());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public boolean desativar(String clientId) {
        int n = em.createNativeQuery("UPDATE api_cliente SET ativo = FALSE WHERE client_id = ?1")
                .setParameter(1, clientId).executeUpdate();
        cache.remove(clientId);
        return n > 0;
    }

    /** Cliente ativo cujo segredo confere, ou null. */
    @Transactional
    public Cliente autenticar(String clientId, String segredo) {
        if (clientId == null || segredo == null || clientId.isBlank() || segredo.isBlank()) {
            return null;
        }
        List<?> linhas = em.createNativeQuery("SELECT segredo_hash FROM api_cliente WHERE client_id = ?1 AND ativo")
                .setParameter(1, clientId.trim()).setMaxResults(1).getResultList();
        if (linhas.isEmpty() || linhas.get(0) == null) {
            return null;
        }
        boolean confere;
        try {
            confere = BCrypt.checkpw(segredo, linhas.get(0).toString());
        } catch (IllegalArgumentException e) {
            confere = false;
        }
        if (!confere) {
            return null;
        }
        em.createNativeQuery("UPDATE api_cliente SET ultimo_uso = now() WHERE client_id = ?1")
                .setParameter(1, clientId.trim()).executeUpdate();
        cache.remove(clientId.trim());
        return buscarAtivo(clientId.trim());
    }

    /** Cliente ativo (cache de 60 s; desativar vale em ate 1 minuto), ou null. */
    public Cliente buscarAtivo(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        long agora = System.currentTimeMillis();
        EmCache c = cache.get(clientId);
        if (c != null && agora - c.lidoEm() < CACHE_MS) {
            return c.cliente();
        }
        List<?> linhas = em.createNativeQuery("SELECT client_id, nome, unidade, permissoes, ativo FROM api_cliente"
                + " WHERE client_id = ?1").setParameter(1, clientId).setMaxResults(1).getResultList();
        Cliente cliente = null;
        if (!linhas.isEmpty() && linhas.get(0) instanceof Object[] l && Boolean.TRUE.equals(l[4])) {
            cliente = new Cliente(String.valueOf(l[0]), String.valueOf(l[1]), String.valueOf(l[2]),
                    normalizarPermissoes(l[3] == null ? "" : l[3].toString()), true);
        }
        cache.put(clientId, new EmCache(cliente, agora));
        return cliente;
    }

    /** Usuario para o token de cliente (claim clientId), ou null se o cliente nao existe/esta inativo. */
    public AuthenticatedUser usuarioDoCliente(String clientId) {
        Cliente c = buscarAtivo(clientId);
        return c == null ? null : paraUsuario(c);
    }
}
