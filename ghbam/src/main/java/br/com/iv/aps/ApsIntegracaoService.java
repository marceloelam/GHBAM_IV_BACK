package br.com.iv.aps;

import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import br.com.iv.service.IntegracaoSusCryptoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Integracoes do G-PEC configuradas pela tela: PEC Centralizador (transmissao LEDI) e
 * banco do PEC para migracao (so leitura). Senha nunca volta para a tela: so "senhaConfigurada".
 */
@ApplicationScoped
public class ApsIntegracaoService {

    public static final String CENTRALIZADOR = "CENTRALIZADOR";
    public static final String MIGRACAO = "MIGRACAO_PEC";

    public record Config(String url, String host, Integer porta, String banco, String usuario, String senha,
            boolean aceitarAutoassinado) {
    }

    @Inject
    ApsDb db;

    @Inject
    IntegracaoSusCryptoService crypto;

    public Map<String, Object> listar() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String chave : List.of(CENTRALIZADOR, MIGRACAO)) {
            Map<String, Object> r = db.one("SELECT * FROM aps_integracao WHERE chave = :c", p("c", chave));
            Map<String, Object> m = new LinkedHashMap<>();
            if (r != null) {
                m.putAll(r);
                m.put("senhaConfigurada", r.get("senha_cripto") != null);
                m.remove("senha_cripto");
            } else {
                m.put("chave", chave);
                m.put("senhaConfigurada", false);
            }
            out.put(chave, m);
        }
        out.put("criptografiaConfigurada", crypto.isConfigured());
        return out;
    }

    /** Config salva pela tela; null se nao houver. Usada pelo transmissor antes das variaveis de ambiente. */
    public Config config(String chave) {
        Map<String, Object> r = db.one("SELECT * FROM aps_integracao WHERE chave = :c", p("c", chave));
        if (r == null) {
            return null;
        }
        String senha = null;
        try {
            senha = crypto.decrypt((String) r.get("senha_cripto"));
        } catch (RuntimeException e) {
            senha = null;
        }
        return new Config(str(r.get("url")), str(r.get("host")), r.get("porta") == null ? null : ((Number) r.get("porta")).intValue(),
                str(r.get("banco")), str(r.get("usuario")), senha, Boolean.TRUE.equals(r.get("aceitar_autoassinado")));
    }

    @Transactional
    public Map<String, Object> salvar(String chave, Map<String, Object> body, String matricula) {
        if (!CENTRALIZADOR.equals(chave) && !MIGRACAO.equals(chave)) {
            throw new IllegalArgumentException("Integracao desconhecida.");
        }
        String url = str(body.get("url"));
        if (CENTRALIZADOR.equals(chave) && url != null && !url.matches("(?i)^https?://.+")) {
            throw new IllegalArgumentException("Informe a URL completa do Centralizador (http:// ou https://).");
        }
        String senha = str(body.get("senha"));
        if (senha != null && !crypto.isConfigured()) {
            throw new IllegalStateException("Chave de criptografia (GHBAM_SUS_CREDENTIALS_KEY) nao configurada no servidor: "
                    + "a senha nao pode ser gravada com seguranca.");
        }
        Integer porta = null;
        if (body.get("porta") != null && !String.valueOf(body.get("porta")).isBlank()) {
            porta = Integer.valueOf(String.valueOf(body.get("porta")).replaceAll("\\D", ""));
        }
        db.exec("""
                INSERT INTO aps_integracao (chave, url, host, porta, banco, usuario, senha_cripto, aceitar_autoassinado,
                    observacao, atualizado_em, atualizado_por)
                VALUES (:c, :url, :host, :porta, :banco, :usuario, :senha, :auto, :obs, CURRENT_TIMESTAMP, :m)
                ON CONFLICT (chave) DO UPDATE SET url = EXCLUDED.url, host = EXCLUDED.host, porta = EXCLUDED.porta,
                    banco = EXCLUDED.banco, usuario = EXCLUDED.usuario,
                    senha_cripto = COALESCE(EXCLUDED.senha_cripto, aps_integracao.senha_cripto),
                    aceitar_autoassinado = EXCLUDED.aceitar_autoassinado, observacao = EXCLUDED.observacao,
                    atualizado_em = CURRENT_TIMESTAMP, atualizado_por = EXCLUDED.atualizado_por
                """, p("c", chave, "url", url, "host", str(body.get("host")), "porta", porta, "banco", str(body.get("banco")),
                "usuario", str(body.get("usuario")), "senha", senha == null ? null : crypto.encrypt(senha),
                "auto", Boolean.TRUE.equals(body.get("aceitar_autoassinado")), "obs", str(body.get("observacao")), "m", matricula));
        return listar();
    }

    @Transactional
    public void registrarTeste(String chave, boolean ok, String mensagem) {
        db.exec("""
                UPDATE aps_integracao SET ultimo_teste_em = CURRENT_TIMESTAMP, ultimo_teste_ok = :ok, ultimo_teste_mensagem = :msg
                WHERE chave = :c
                """, p("c", chave, "ok", ok, "msg", mensagem == null ? null : mensagem.length() > 1000 ? mensagem.substring(0, 1000) : mensagem));
    }

    /**
     * Conecta ao banco do PEC em modo SO LEITURA e faz um inventario leve (versao do PostgreSQL e
     * tabelas encontradas). Nao le dados de cidadaos.
     */
    public Map<String, Object> testarMigracao() {
        Config c = config(MIGRACAO);
        if (c == null || c.host() == null || c.banco() == null || c.usuario() == null) {
            throw new IllegalStateException("Preencha servidor, banco e usuario do banco do PEC e salve antes de testar.");
        }
        String jdbc = "jdbc:postgresql://" + c.host() + ":" + (c.porta() == null ? 5433 : c.porta()) + "/" + c.banco();
        Properties pr = new Properties();
        pr.setProperty("user", c.usuario());
        if (c.senha() != null) {
            pr.setProperty("password", c.senha());
        }
        pr.setProperty("connectTimeout", "10");
        pr.setProperty("socketTimeout", "30");
        pr.setProperty("readOnly", "true");
        pr.setProperty("ApplicationName", "GHBAM G-PEC migracao (leitura)");
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection con = DriverManager.getConnection(jdbc, pr)) {
            con.setReadOnly(true);
            try (Statement st = con.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT version()")) {
                    if (rs.next()) {
                        out.put("postgres", rs.getString(1));
                    }
                }
                try (ResultSet rs = st.executeQuery("""
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema NOT IN ('pg_catalog', 'information_schema')""")) {
                    if (rs.next()) {
                        out.put("tabelas", rs.getLong(1));
                    }
                }
                List<String> exemplos = new ArrayList<>();
                try (ResultSet rs = st.executeQuery("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name LIKE 'tb\\_%' ORDER BY table_name LIMIT 15""")) {
                    while (rs.next()) {
                        exemplos.add(rs.getString(1));
                    }
                }
                out.put("exemplosTabelas", exemplos);
            }
            out.put("ok", true);
            out.put("mensagem", "Conexao somente leitura estabelecida.");
        } catch (Exception e) {
            out.put("ok", false);
            out.put("mensagem", "Falha ao conectar: " + e.getMessage());
        }
        registrarTeste(MIGRACAO, Boolean.TRUE.equals(out.get("ok")), (String) out.get("mensagem"));
        return out;
    }
}
