package br.com.iv.ad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import io.agroal.api.AgroalDataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Hashtable;

@ApplicationScoped
public class ActiveDirectoryService {

    private static final Logger LOG = Logger.getLogger(ActiveDirectoryService.class);

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "LDAP_SERVER_URI")
    String ldapServerUri;

    @ConfigProperty(name = "ldap.domain", defaultValue = "itg.rio")
    String ldapDomain;

    @ConfigProperty(name = "ldap.admin.user", defaultValue = "")
    String ldapAdminUser;

    @ConfigProperty(name = "ldap.admin.password", defaultValue = "")
    String ldapAdminPassword;

    // DataSource é injetado diretamente, não precisa mais das configurações individuais

    // autenticação por bind simples: tenta fazer bind diretamente com usuário fornecido
    public boolean autenticar(String usuario, String senha) {

        if (usuario == null || usuario.trim().isEmpty() || senha == null) {
            LOG.warn("Credenciais inválidas recebidas");
            return false;
        }

        String user = usuario.trim();

        String principal;
       
        if (user.contains("@") || user.contains("\\")) {
            principal = user;
        } else {
            principal = user + "@" + ldapDomain;
        }

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapServerUri);

        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, senha);

        // evita travar thread (ms)
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        // seguir referrals retornados pelo AD (evita PartialResultException quando houver referências para outros servidores)
        env.put(Context.REFERRAL, "follow");

        try {
            DirContext ctx = new InitialDirContext(env);
            try {
                ctx.close();
            } catch (NamingException ex) {
                LOG.debug("Erro ao fechar contexto LDAP", ex);
            }
            return true;
        } catch (javax.naming.PartialResultException pre) {
            LOG.debugf(pre, "PartialResultException durante autenticação para %s", user);
            return false;
        } catch (NamingException e) {
            LOG.warnf(e, "Falha de autenticação LDAP para usuario %s", user);
            return false;
        }
    }

    // busca todos os atributos do usuário no AD e retorna um mapa nome->valor
    public Map<String, String> buscarAtributos(String usuario, String senha) {
        if (usuario == null || usuario.trim().isEmpty()) {
            LOG.warn("Usuário inválido para busca de atributos");
            return Collections.emptyMap();
        }

        String user = usuario.trim();

        String principal;
        if (user.contains("@") || user.contains("\\")) {
            principal = user;
        } else {
            principal = user + "@" + ldapDomain;
        }

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapServerUri);

        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, senha);

        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put(Context.REFERRAL, "follow");

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);

            String baseDN = buildBaseDN(ldapDomain);
            LOG.infof("Buscando atributos para usuário %s no baseDN: %s", user, baseDN);

            String filter;
            if (user.contains("@")) {
                filter = "(userPrincipalName=" + escapeLDAP(user) + ")";
            } else if (user.contains("\\")) {
                String s = user.substring(user.indexOf('\\') + 1);
                filter = "(sAMAccountName=" + escapeLDAP(s) + ")";
            } else {
                filter = "(|(sAMAccountName=" + escapeLDAP(user) + ")(userPrincipalName=" + escapeLDAP(user + "@" + ldapDomain) + "))";
            }

            LOG.infof("Filtro LDAP: %s", filter);

            SearchControls sc = new SearchControls();
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
            sc.setReturningAttributes(null); // retorna todos os atributos

            NamingEnumeration<SearchResult> results = ctx.search(baseDN, filter, sc);

            Map<String, String> attrsMap = new HashMap<>();

            int count = 0;
            while (results.hasMore()) {
                SearchResult sr = results.next();
                count++;
                LOG.infof("Resultado %d encontrado: %s", count, sr.getName());
                
                Attributes attrs = sr.getAttributes();
                if (attrs == null) {
                    LOG.warn("Nenhum atributo encontrado no resultado");
                    continue;
                }

                NamingEnumeration<? extends Attribute> all = attrs.getAll();
                while (all.hasMore()) {
                    Attribute a = all.next();
                    NamingEnumeration<?> vals = a.getAll();
                    List<String> values = new ArrayList<>();
                    while (vals.hasMore()) {
                        Object v = vals.next();
                        values.add(v == null ? "" : String.valueOf(v));
                    }
                    String attrValue = String.join(",", values);
                    attrsMap.put(a.getID(), attrValue);
                    LOG.debugf("Atributo: %s = %s", a.getID(), attrValue.length() > 100 ? attrValue.substring(0, 100) + "..." : attrValue);
                }
            }

            LOG.infof("Total de atributos encontrados: %d", attrsMap.size());
            LOG.infof("Total de atributos encontrados: %d", attrsMap.size());
            return attrsMap;
        } catch (javax.naming.PartialResultException pre) {
            LOG.warnf(pre, "PartialResultException ao buscar atributos para %s - isto pode indicar que o usuário foi encontrado mas alguns atributos não puderam ser recuperados", user);
            return Collections.emptyMap();
        } catch (NamingException e) {
            LOG.warnf(e, "Erro buscando atributos LDAP para %s", user);
            return Collections.emptyMap();
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOG.debug("Erro ao fechar contexto LDAP", ex);
                }
            }
        }
    }

    public String buildBaseDN(String domain) {
        if (domain == null || domain.trim().isEmpty()) return "";
        String[] parts = domain.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("dc=").append(parts[i]);
        }
        String result = sb.toString();
        LOG.debugf("BaseDN construído a partir de '%s': %s", domain, result);
        return result;
    }

    private String escapeLDAP(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("*", "\\*").replace("(", "\\(").replace(")", "\\)");
    }

    /**
     * Verifica se um usuário existe no AD e se está ativo (baseado no userAccountControl)
     * Usa credenciais de administrador configuradas para fazer a consulta
     * 
     * @param usuario Nome do usuário a ser verificado
     * @return Map contendo "existe" (boolean), "ativo" (boolean), "userAccountControl" (string) e "mensagem"
     */
    public Map<String, Object> verificarStatusUsuario(String usuario) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("existe", false);
        resultado.put("ativo", false);
        resultado.put("userAccountControl", "");
        resultado.put("mensagem", "");

        if (usuario == null || usuario.trim().isEmpty()) {
            resultado.put("mensagem", "Usuário inválido");
            return resultado;
        }

        if (ldapAdminUser == null || ldapAdminUser.trim().isEmpty() || 
            ldapAdminPassword == null || ldapAdminPassword.trim().isEmpty()) {
            resultado.put("mensagem", "Credenciais de administrador não configuradas. Configure LDAP_ADMIN_USER e LDAP_ADMIN_PASSWORD");
            return resultado;
        }

        String user = usuario.trim();
        String adminPrincipal = ldapAdminUser.contains("@") ? ldapAdminUser : ldapAdminUser + "@" + ldapDomain;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapServerUri);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, adminPrincipal);
        env.put(Context.SECURITY_CREDENTIALS, ldapAdminPassword);
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put(Context.REFERRAL, "follow");

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            String baseDN = buildBaseDN(ldapDomain);

            String filter;
            if (user.contains("@")) {
                filter = "(userPrincipalName=" + escapeLDAP(user) + ")";
            } else if (user.contains("\\")) {
                String s = user.substring(user.indexOf('\\') + 1);
                filter = "(sAMAccountName=" + escapeLDAP(s) + ")";
            } else {
                filter = "(|(sAMAccountName=" + escapeLDAP(user) + ")(userPrincipalName=" + escapeLDAP(user + "@" + ldapDomain) + "))";
            }

            SearchControls sc = new SearchControls();
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
            sc.setReturningAttributes(new String[]{"userAccountControl", "sAMAccountName", "cn"});

            NamingEnumeration<SearchResult> results = ctx.search(baseDN, filter, sc);

            if (results.hasMore()) {
                SearchResult sr = results.next();
                resultado.put("existe", true);
                
                Attributes attrs = sr.getAttributes();
                Attribute uacAttr = attrs.get("userAccountControl");
                
                if (uacAttr != null) {
                    String uacValue = String.valueOf(uacAttr.get());
                    resultado.put("userAccountControl", uacValue);
                    
                    try {
                        int uac = Integer.parseInt(uacValue);
                        // Bit 2 (valor 2) indica conta desabilitada
                        // Se (uac & 2) == 0, a conta está ativa
                        boolean ativo = (uac & 2) == 0;
                        resultado.put("ativo", ativo);
                        
                        if (ativo) {
                            resultado.put("mensagem", "Usuário existe e está ativo no AD");
                        } else {
                            resultado.put("mensagem", "Usuário existe mas está desabilitado no AD");
                        }
                    } catch (NumberFormatException e) {
                        LOG.warnf("Erro ao converter userAccountControl: %s", uacValue);
                        resultado.put("mensagem", "Usuário existe mas userAccountControl inválido");
                    }
                } else {
                    resultado.put("mensagem", "Usuário existe mas atributo userAccountControl não encontrado");
                }
                
                // Adicionar informações extras se disponíveis
                Attribute cnAttr = attrs.get("cn");
                if (cnAttr != null) {
                    resultado.put("cn", String.valueOf(cnAttr.get()));
                }
                
                Attribute samAttr = attrs.get("sAMAccountName");
                if (samAttr != null) {
                    resultado.put("sAMAccountName", String.valueOf(samAttr.get()));
                }
            } else {
                resultado.put("mensagem", "Usuário não encontrado no AD");
            }

        } catch (javax.naming.PartialResultException pre) {
            LOG.warnf(pre, "PartialResultException ao verificar status do usuário %s", user);
            resultado.put("mensagem", "Erro parcial ao consultar AD: " + pre.getMessage());
        } catch (NamingException e) {
            LOG.warnf(e, "Erro ao verificar status do usuário %s no AD", user);
            resultado.put("mensagem", "Erro ao consultar AD: " + e.getMessage());
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOG.debug("Erro ao fechar contexto LDAP", ex);
                }
            }
        }

        return resultado;
    }

    /**
     * Lista usuários de um OU específico, retornando cn, description e sAMAccountName
     */
    public List<Map<String, String>> listarUsuariosPorOU(String ou) {
        List<Map<String, String>> usuarios = new ArrayList<>();
        if (ou == null || ou.trim().isEmpty()) {
            LOG.warn("OU inválido para busca de usuários");
            return usuarios;
        }

        if (ldapAdminUser == null || ldapAdminUser.trim().isEmpty() || ldapAdminPassword == null || ldapAdminPassword.trim().isEmpty()) {
            LOG.warn("Credenciais de administrador não configuradas");
            return usuarios;
        }

        String adminPrincipal = ldapAdminUser.contains("@") ? ldapAdminUser : ldapAdminUser + "@" + ldapDomain;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapServerUri);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, adminPrincipal);
        env.put(Context.SECURITY_CREDENTIALS, ldapAdminPassword);
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put(Context.REFERRAL, "follow");

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            // O OU pode ser passado como DN completo ou apenas o nome, aqui assumo DN completo
            String baseDN = ou.trim();
            String filter = "(objectClass=user)";
            SearchControls sc = new SearchControls();
            sc.setSearchScope(SearchControls.ONELEVEL_SCOPE); // apenas usuários diretamente no OU
            sc.setReturningAttributes(new String[]{"cn", "description", "sAMAccountName"});

            NamingEnumeration<SearchResult> results = ctx.search(baseDN, filter, sc);
            while (results.hasMore()) {
                SearchResult sr = results.next();
                Attributes attrs = sr.getAttributes();
                Map<String, String> usuario = new HashMap<>();
                usuario.put("cn", attrs.get("cn") != null ? attrs.get("cn").get().toString() : "");
                usuario.put("description", attrs.get("description") != null ? attrs.get("description").get().toString() : "");
                usuario.put("usuario", attrs.get("sAMAccountName") != null ? attrs.get("sAMAccountName").get().toString() : "");
                usuarios.add(usuario);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Erro ao buscar usuários do OU %s", ou);
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (NamingException ex) { LOG.debug("Erro ao fechar contexto LDAP", ex); }
            }
        }
        return usuarios;
    }

    /**
     * Lista todos os OUs (Organizational Units) a partir de um baseDN
     */
    public List<String> listarOUs(String baseDN) {
        List<String> ous = new ArrayList<>();
        if (ldapAdminUser == null || ldapAdminUser.trim().isEmpty() || ldapAdminPassword == null || ldapAdminPassword.trim().isEmpty()) {
            LOG.warn("Credenciais de administrador não configuradas");
            return ous;
        }
        String adminPrincipal = ldapAdminUser.contains("@") ? ldapAdminUser : ldapAdminUser + "@" + ldapDomain;
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapServerUri);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, adminPrincipal);
        env.put(Context.SECURITY_CREDENTIALS, ldapAdminPassword);
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put(Context.REFERRAL, "follow");
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            String filter = "(objectClass=organizationalUnit)";
            SearchControls sc = new SearchControls();
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
            sc.setReturningAttributes(new String[]{"ou", "distinguishedName"});
            NamingEnumeration<SearchResult> results = ctx.search(baseDN, filter, sc);
            while (results.hasMore()) {
                SearchResult sr = results.next();
                Attributes attrs = sr.getAttributes();
                String ouName = attrs.get("ou") != null ? attrs.get("ou").get().toString() : "";
                String dn = attrs.get("distinguishedName") != null ? attrs.get("distinguishedName").get().toString() : "";
                if (!ouName.isEmpty() && !dn.isEmpty()) {
                    ous.add(dn);
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Erro ao buscar OUs do baseDN %s", baseDN);
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (NamingException ex) { LOG.debug("Erro ao fechar contexto LDAP", ex); }
            }
        }
        return ous;
    }

    /**
     * Atualiza o campo usuarioad na tabela servidor onde nome (maiusculo) == cn (maiusculo)
     * Retorna a quantidade de registros atualizados
     */
    public int atualizarUsuarioAdServidorPorNome(String nomeMaiusculo, String usuarioAd) {
        int atualizados = 0;
        java.sql.Connection conn = null;
        java.sql.PreparedStatement stmt = null;
        try {
            // Remove espaços extras dos parâmetros
            String nomeTrim = nomeMaiusculo != null ? nomeMaiusculo.trim().replaceAll("\\s+", "") : "";
            String usuarioAdTrim = usuarioAd != null ? usuarioAd.trim().replaceAll("\\s+", "") : "";
            // USA O DATASOURCE GERENCIADO EM VEZ DE DRIVERMANAGER
            conn = dataSource.getConnection();
            String sql = "UPDATE servidor SET usuarioad = ? WHERE REPLACE(UPPER(nome), ' ', '') = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, usuarioAdTrim);
            stmt.setString(2, nomeTrim.toUpperCase());
            atualizados = stmt.executeUpdate();
        } catch (Exception e) {
            LOG.warnf(e, "Erro ao atualizar usuarioad para nome %s", nomeMaiusculo);
        } finally {
            try { if (stmt != null) stmt.close(); } catch (Exception e) {}
            try { if (conn != null) conn.close(); } catch (Exception e) {}
        }
        return atualizados;
    }
    

    /**
     * Atualiza o campo usuarioad na tabela servidor onde nome (maiusculo) == cn (maiusculo)
     * Retorna a quantidade de registros atualizados
     */
    public int atualizarUsuarioAdServidorPorNome2(String matricula, String usuarioAd) {
        int atualizados = 0;
        java.sql.Connection conn = null;
        java.sql.PreparedStatement stmt = null;
        try {
            // Remove espaços extras dos parâmetros
            String nomeTrim = matricula != null ? matricula.trim().replaceAll("\\s+", "") : "";
            String usuarioAdTrim = usuarioAd != null ? usuarioAd.trim().replaceAll("\\s+", "") : "";
            // USA O DATASOURCE GERENCIADO EM VEZ DE DRIVERMANAGER
            conn = dataSource.getConnection();
            String sql = "UPDATE servidor SET usuarioad = ? WHERE REPLACE(UPPER(matricula), ' ', '') = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, usuarioAdTrim);
            stmt.setString(2, nomeTrim.toUpperCase());
            atualizados = stmt.executeUpdate();
        } catch (Exception e) {
            LOG.warnf(e, "Erro ao atualizar usuarioad para nome %s", matricula);
        } finally {
            try { if (stmt != null) stmt.close(); } catch (Exception e) {}
            try { if (conn != null) conn.close(); } catch (Exception e) {}
        }
        return atualizados;
    }

}
