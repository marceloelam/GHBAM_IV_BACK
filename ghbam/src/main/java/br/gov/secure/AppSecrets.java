package br.gov.secure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Compatibilidade para consumidores legados de configuracao.
 *
 * <p>Nenhum segredo pode ser definido nesta classe. Por padrao, os valores sao
 * lidos de {@code config/ghbam-secrets.properties}, um arquivo externo ao JAR e
 * ignorado pelo Git. Variaveis de ambiente permanecem apenas como fallback de
 * compatibilidade.</p>
 */
public final class AppSecrets {

    private static final String DEFAULT_SECRETS_FILE = "config/ghbam-secrets.properties";
    private static final Properties FILE_PROPERTIES = loadProperties();

    public static final String DB_USER = value("DB_USER");
    public static final String DB_PASSWORD = value("DB_PASSWORD");
    public static final String DB_HOST = value("DB_HOST");
    public static final String DB_PORT = valueOrDefault("DB_PORT", "5432");
    public static final String DB_NAME = value("DB_NAME");

    public static final String LDAP_SERVER_URI = value("LDAP_SERVER_URI");
    public static final String LDAP_BIND_DN = value("LDAP_BIND_DN");
    public static final String LDAP_BIND_PASSWORD = value("LDAP_BIND_PASSWORD");
    public static final String LDAP_USER_SEARCH_BASE = value("LDAP_USER_SEARCH_BASE");
    public static final String LDAP_ADMIN_USER = value("LDAP_ADMIN_USER");
    public static final String LDAP_ADMIN_PASSWORD = value("LDAP_ADMIN_PASSWORD");
    public static final String LDAP_DOMAIN = value("LDAP_DOMAIN");

    public static final String ADMIN_RECOVERY_MATRICULA = value("ADMIN_RECOVERY_MATRICULA");
    public static final String ADMIN_RECOVERY_PASSWORD = value("ADMIN_RECOVERY_PASSWORD");

    public static final String VAPID_PUBLIC_KEY = value("VAPID_PUBLIC_KEY");
    public static final String VAPID_PRIVATE_KEY = value("VAPID_PRIVATE_KEY");
    public static final String VAPID_SUBJECT = value("VAPID_SUBJECT");

    public static final String REPORTS_DIR = valueOrDefault("REPORTS_DIR", "src/main/resources/reports");
    public static final String HF_API_TOKEN = value("HF_API_TOKEN");
    public static final String OPENROUTER_API_KEY = value("OPENROUTER_API_KEY");
    public static final String GROQ_API_KEY = value("GROQ_API_KEY");
    public static final String GOOGLE_GENAI_API_KEY = value("GOOGLE_GENAI_API_KEY");
    public static final String OPENAI_API_KEY = value("OPENAI_API_KEY");
    public static final String TRANSCRIPTION_API_KEY = value("TRANSCRIPTION_API_KEY");
    public static final String TRANSCRIPTION_API_URL = value("TRANSCRIPTION_API_URL");
    public static final String TRANSCRIPTION_MODEL = value("TRANSCRIPTION_MODEL");
    public static final String HF_MODEL = value("HF_MODEL");
    public static final String CLAUD_RAG_API_KEY = value("CLAUD_RAG_API_KEY");
    public static final String CLAUD_RAG_MODEL = value("CLAUD_RAG_MODEL");

    public static final boolean ENABLE_SET_AUTOCOMMIT_LOG =
            Boolean.parseBoolean(valueOrDefault("ENABLE_SET_AUTOCOMMIT_LOG", "false"));

    private static String value(String name) {
        return valueOrDefault(name, "");
    }

    private static String valueOrDefault(String name, String defaultValue) {
        String systemValue = System.getProperty(name);
        if (present(systemValue)) {
            return normalize(systemValue);
        }

        String fileValue = FILE_PROPERTIES.getProperty(name);
        if (present(fileValue)) {
            return normalize(fileValue);
        }

        String environmentValue = System.getenv(name);
        return present(environmentValue) ? normalize(environmentValue) : defaultValue;
    }

    private static Properties loadProperties() {
        String configuredPath = System.getProperty("ghbam.secrets.file", DEFAULT_SECRETS_FILE);
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Arquivo de segredos invalido: " + path);
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler o arquivo de segredos: " + path, e);
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        String normalized = value.trim();
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return normalized.substring(1, normalized.length() - 1);
            }
        }
        return normalized;
    }

    static Map<String, String> fileProperties() {
        Map<String, String> values = new HashMap<>();
        for (String name : FILE_PROPERTIES.stringPropertyNames()) {
            String value = FILE_PROPERTIES.getProperty(name);
            if (present(value)) {
                values.put(name, normalize(value));
            }
        }
        return Map.copyOf(values);
    }

    private AppSecrets() {
    }
}

