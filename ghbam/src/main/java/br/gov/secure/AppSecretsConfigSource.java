package br.gov.secure;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Ponte de compatibilidade para propriedades legadas.
 *
 * <p>Os valores sao lidos exclusivamente do ambiente por {@link AppSecrets}.
 * Propriedades ausentes nao sao publicadas, permitindo que o Quarkus aplique
 * sua resolucao padrao e que o validador interrompa um startup inseguro.</p>
 */
public class AppSecretsConfigSource implements ConfigSource {

    private static final int PRIORITY = 600;
    private static final String SOURCE_NAME = "ghbam-environment-secrets";
    private static final Map<String, String> PROPERTIES = buildProperties();

    private static Map<String, String> buildProperties() {
        Map<String, String> map = new HashMap<>(AppSecrets.fileProperties());

        putIfPresent(map, "DB_USER", AppSecrets.DB_USER);
        putIfPresent(map, "DB_PASSWORD", AppSecrets.DB_PASSWORD);
        putIfPresent(map, "DB_HOST", AppSecrets.DB_HOST);
        putIfPresent(map, "DB_PORT", AppSecrets.DB_PORT);
        putIfPresent(map, "DB_NAME", AppSecrets.DB_NAME);

        if (present(AppSecrets.DB_HOST) && present(AppSecrets.DB_PORT) && present(AppSecrets.DB_NAME)) {
            map.put("quarkus.datasource.jdbc.url",
                    "jdbc:postgresql://" + AppSecrets.DB_HOST + ":" + AppSecrets.DB_PORT + "/"
                            + AppSecrets.DB_NAME
                            + "?tcpKeepAlive=true&loginTimeout=30&connectTimeout=30&socketTimeout=120"
                            + "&applicationName=ghbam-app&reWriteBatchedInserts=true");
        }

        putIfPresent(map, "secure.app.database.username", AppSecrets.DB_USER);
        putIfPresent(map, "secure.app.database.password", AppSecrets.DB_PASSWORD);
        putIfPresent(map, "secure.app.database.host", AppSecrets.DB_HOST);
        putIfPresent(map, "secure.app.database.port", AppSecrets.DB_PORT);
        putIfPresent(map, "secure.app.database.name", AppSecrets.DB_NAME);

        putIfPresent(map, "LDAP_SERVER_URI", AppSecrets.LDAP_SERVER_URI);
        putIfPresent(map, "LDAP_BIND_DN", AppSecrets.LDAP_BIND_DN);
        putIfPresent(map, "LDAP_BIND_PASSWORD", AppSecrets.LDAP_BIND_PASSWORD);
        putIfPresent(map, "LDAP_USER_SEARCH_BASE", AppSecrets.LDAP_USER_SEARCH_BASE);
        putIfPresent(map, "LDAP_ADMIN_USER", AppSecrets.LDAP_ADMIN_USER);
        putIfPresent(map, "LDAP_ADMIN_PASSWORD", AppSecrets.LDAP_ADMIN_PASSWORD);
        putIfPresent(map, "LDAP_DOMAIN", AppSecrets.LDAP_DOMAIN);
        putIfPresent(map, "REPORTS_DIR", AppSecrets.REPORTS_DIR);
        putIfPresent(map, "HF_API_TOKEN", AppSecrets.HF_API_TOKEN);
        putIfPresent(map, "GOOGLE_GENAI_API_KEY", AppSecrets.GOOGLE_GENAI_API_KEY);

        return Map.copyOf(map);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (present(value)) {
            map.put(key, value);
        }
    }

    @Override
    public Map<String, String> getProperties() {
        return PROPERTIES;
    }

    @Override
    public Set<String> getPropertyNames() {
        return PROPERTIES.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return PROPERTIES.get(propertyName);
    }

    @Override
    public String getName() {
        return SOURCE_NAME;
    }

    @Override
    public int getOrdinal() {
        return PRIORITY;
    }
}
