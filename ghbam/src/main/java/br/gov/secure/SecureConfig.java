package br.gov.secure;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Mapeamento seguro das configurações sensíveis da aplicação.
 *
 * <p>Utiliza {@link ConfigMapping} do SmallRye Config para leitura type-safe.
 * Os valores reais são resolvidos pelo Quarkus a partir das expressões definidas
 * em {@code application.properties} (ex.: {@code ${DB_PASSWORD}}), que por sua vez
 * lêem as variáveis de ambiente — nunca senhas em texto plano no artefato compilado.</p>
 *
 * <p>Variáveis de ambiente obrigatórias em produção:
 * <ul>
 *   <li>{@code DB_PASSWORD}          – senha do banco de dados PostgreSQL</li>
 *   <li>{@code LDAP_BIND_PASSWORD}   – senha de bind do Active Directory</li>
 *   <li>{@code LDAP_ADMIN_PASSWORD}  – senha do usuário admin LDAP</li>
 * </ul>
 * </p>
 *
 * <p>Para desenvolvimento local, crie um arquivo {@code .env} na raiz do projeto
 * (já adicionado ao {@code .gitignore}) com as variáveis acima.</p>
 *
 * <p>Correspondência com o {@code application.properties}:
 * <pre>
 *   secure.app.database.password    = ${DB_PASSWORD}
 *   secure.app.ldap.bind-password   = ${LDAP_BIND_PASSWORD}
 *   secure.app.ldap.admin-password  = ${LDAP_ADMIN_PASSWORD}
 * </pre>
 * </p>
 */
@ConfigMapping(prefix = "secure.app")
public interface SecureConfig {

    /** Configurações do banco de dados */
    Database database();

    /** Configurações do LDAP / Active Directory */
    Ldap ldap();

    /** Configurações da aplicação */
    App app();

    // ------------------------------------------------------------------ //
    //  Grupos de configuração                                              //
    // ------------------------------------------------------------------ //

    interface Database {

        /** Usuário do banco de dados.  Padrão definido em application.properties. */
        @WithName("username")
        String username();

        /**
         * Senha do banco de dados.
         * <strong>OBRIGATÓRIA via variável de ambiente {@code DB_PASSWORD}.</strong>
         * Sem valor padrão — a ausência causa falha no startup.
         */
        @WithName("password")
        String password();

        /** Host do banco de dados. */
        @WithName("host")
        String host();

        /** Porta do banco de dados. */
        @WithName("port")
        int port();

        /** Nome do banco de dados. */
        @WithName("name")
        String name();
    }

    interface Ldap {

        /** URL do servidor LDAP. */
        @WithName("server-uri")
        String serverUri();

        /** DN de bind para autenticação no AD. */
        @WithName("bind-dn")
        String bindDn();

        /**
         * Senha de bind do Active Directory.
         * <strong>OBRIGATÓRIA via variável de ambiente {@code LDAP_BIND_PASSWORD}.</strong>
         */
        @WithName("bind-password")
        String bindPassword();

        /** Base DN para pesquisa de usuários. */
        @WithName("search-base-dn")
        String searchBaseDn();

        /** Usuário administrador LDAP. */
        @WithName("admin-user")
        String adminUser();

        /**
         * Senha do administrador LDAP.
         * <strong>OBRIGATÓRIA via variável de ambiente {@code LDAP_ADMIN_PASSWORD}.</strong>
         */
        @WithName("admin-password")
        String adminPassword();

        /** Domínio do Active Directory. */
        @WithName("domain")
        String domain();
    }

    interface App {

        /** Diretório dos relatórios Jasper. */
        @WithName("reports-dir")
        String reportsDir();

        /** Issuer JWT. */
        @WithName("jwt-issuer")
        String jwtIssuer();
    }

    // ------------------------------------------------------------------ //
    //  Utilitários estáticos de mascaramento (uso em logs/debug)          //
    // ------------------------------------------------------------------ //

    /**
     * Mascara um valor sensível, exibindo apenas os dois primeiros caracteres.
     * Retorna {@code "***"} para valores nulos ou muito curtos.
     *
     * @param value valor sensível
     * @return string mascarada, adequada para logs
     */
    static String mask(String value) {
        if (value == null || value.isBlank()) return "[NÃO DEFINIDO]";
        if (value.length() <= 3) return "***";
        return value.substring(0, 2) + "*".repeat(Math.min(value.length() - 2, 6));
    }

    /**
     * Remove credenciais embutidas de uma JDBC URL antes de registrá-la em log.
     * Ex.: jdbc:postgresql://host:5432/db?password=secret → password=****
     *
     * @param jdbcUrl URL JDBC completa
     * @return URL com senha substituída por {@code ****}
     */
    static String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) return "[NÃO DEFINIDA]";
        return jdbcUrl.replaceAll("(?i)(password=)[^&\\s]*", "$1****");
    }
}
