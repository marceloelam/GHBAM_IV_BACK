package br.gov.secure;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validador de configurações sensíveis executado na inicialização da aplicação.
 *
 * <p>Verifica se todas as variáveis de ambiente obrigatórias estão presentes e
 * registra o estado da configuração no log com valores mascarados (nunca em
 * texto plano). A aplicação falhará no startup de forma segura caso algum
 * segredo obrigatório não esteja definido.</p>
 *
 * <h3>Variáveis obrigatórias</h3>
 * <ul>
 *   <li>{@code DB_PASSWORD}          – senha do banco de dados PostgreSQL</li>
 *   <li>{@code LDAP_BIND_PASSWORD}   – senha de bind do Active Directory</li>
 *   <li>{@code LDAP_ADMIN_PASSWORD}  – senha do usuário admin LDAP</li>
 * </ul>
 *
 * <h3>Como fornecer os segredos</h3>
 * <ul>
 *   <li><b>Produção:</b> defina as variáveis de ambiente no host / container / serviço.</li>
 *   <li><b>Desenvolvimento local:</b> crie um arquivo {@code .env} na raiz do projeto
 *       (já listado no {@code .gitignore}).</li>
 * </ul>
 */
@ApplicationScoped
public class SecureConfigValidator {

    private static final Logger LOG = Logger.getLogger(SecureConfigValidator.class);

    // ------------------------------------------------------------------ //
    //  Banco de dados                                                     //
    // ------------------------------------------------------------------ //

    @ConfigProperty(name = "quarkus.datasource.username")
    Optional<String> dbUsername;

    @ConfigProperty(name = "quarkus.datasource.password")
    Optional<String> dbPassword;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    Optional<String> dbJdbcUrl;

    // ------------------------------------------------------------------ //
    //  LDAP / Active Directory                                            //
    // ------------------------------------------------------------------ //

    @ConfigProperty(name = "quarkus.security.ldap.dir-context.principal")
    Optional<String> ldapBindDn;

    @ConfigProperty(name = "quarkus.security.ldap.dir-context.password")
    Optional<String> ldapBindPassword;

    @ConfigProperty(name = "ldap.admin.user")
    Optional<String> ldapAdminUser;

    @ConfigProperty(name = "ldap.admin.password")
    Optional<String> ldapAdminPassword;

    // ------------------------------------------------------------------ //
    //  JWT                                                                //
    // ------------------------------------------------------------------ //

    @ConfigProperty(name = "smallrye.jwt.sign.key.location")
    Optional<String> jwtSignKeyLocation;

    // ------------------------------------------------------------------ //
    //  Inicialização                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Executado imediatamente após o CDI container ser inicializado.
     * Valida os segredos obrigatórios e impede o startup em caso de ausência.
     */
    void onStart(@Observes StartupEvent event) {
        LOG.info("╔══════════════════════════════════════════════╗");
        LOG.info("║  SecureConfigValidator – verificando segredos ║");
        LOG.info("╚══════════════════════════════════════════════╝");

        List<String> faltando = validarSegredosObrigatorios();

        logStatus();

        if (!faltando.isEmpty()) {
            String mensagem = "Configuração insegura: as seguintes variáveis de ambiente " +
                    "obrigatórias não estão definidas → " + String.join(", ", faltando) +
                    ". Defina-as no ambiente de execução ou no arquivo .env (desenvolvimento).";
            LOG.error(mensagem);
            throw new IllegalStateException(mensagem);
        }

        LOG.info("✔ Todos os segredos obrigatórios foram validados com sucesso.");
    }

    // ------------------------------------------------------------------ //
    //  Implementação privada                                              //
    // ------------------------------------------------------------------ //

    /**
     * Verifica se cada segredo obrigatório está presente e não é vazio.
     *
     * @return lista com os nomes das variáveis de ambiente ausentes
     */
    private List<String> validarSegredosObrigatorios() {
        List<String> ausentes = new ArrayList<>();

        checar(ausentes, dbPassword,       "DB_PASSWORD",         "quarkus.datasource.password");
        checar(ausentes, ldapBindPassword, "LDAP_BIND_PASSWORD",  "quarkus.security.ldap.dir-context.password");
        checar(ausentes, ldapAdminPassword,"LDAP_ADMIN_PASSWORD", "ldap.admin.password");
        checarValor(ausentes, AppSecrets.VAPID_PUBLIC_KEY, "VAPID_PUBLIC_KEY");
        checarValor(ausentes, AppSecrets.VAPID_PRIVATE_KEY, "VAPID_PRIVATE_KEY");
        checarValor(ausentes, AppSecrets.VAPID_SUBJECT, "VAPID_SUBJECT");

        return ausentes;
    }

    /**
     * Registra o status de cada propriedade sensível no log (valores mascarados).
     */
    private void logStatus() {
        LOG.infof("  DB_USER       : %s", SecureConfig.mask(dbUsername.orElse(null)));
        LOG.infof("  DB_PASSWORD   : %s", dbPassword.isPresent()       ? "[DEFINIDO]" : "[NÃO DEFINIDO ⚠]");
        LOG.infof("  DB_JDBC_URL   : %s", SecureConfig.maskJdbcUrl(dbJdbcUrl.orElse(null)));
        LOG.infof("  LDAP_BIND_DN  : %s", SecureConfig.mask(ldapBindDn.orElse(null)));
        LOG.infof("  LDAP_PASSWORD : %s", ldapBindPassword.isPresent() ? "[DEFINIDO]" : "[NÃO DEFINIDO ⚠]");
        LOG.infof("  LDAP_ADMIN_PW : %s", ldapAdminPassword.isPresent()? "[DEFINIDO]" : "[NÃO DEFINIDO ⚠]");

        // Aviso (não bloqueia): chave privada JWT empacotada no .jar em produção.
        boolean jwtNoClasspath = jwtSignKeyLocation.map(l -> l.startsWith("classpath:")).orElse(true);
        LOG.infof("  JWT_PRIV_KEY  : %s", jwtNoClasspath ? "[CLASSPATH]" : "[EXTERNA]");
        if (jwtNoClasspath && LaunchMode.current() == LaunchMode.NORMAL) {
            LOG.warn("  ⚠ Chave privada JWT lida de dentro do .jar. Defina GHBAM_JWT_PRIVATE_KEY_LOCATION "
                    + "e GHBAM_JWT_PUBLIC_KEY_LOCATION apontando para arquivos externos (file:...).");
        }
    }

    /**
     * Adiciona o nome da variável à lista de ausentes se o valor não estiver definido.
     *
     * @param ausentes    lista de variáveis ausentes (mutável)
     * @param valor       valor lido da configuração
     * @param envVarNome  nome da variável de ambiente esperada
     * @param propNome    nome da propriedade no application.properties
     */
    private void checar(List<String> ausentes,
                        Optional<String> valor,
                        String envVarNome,
                        String propNome) {
        if (valor.isEmpty() || valor.get().isBlank()) {
            LOG.errorf("  ✘ Segredo ausente: env=%s / prop=%s", envVarNome, propNome);
            ausentes.add(envVarNome);
        }
    }

    private void checarValor(List<String> ausentes, String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            LOG.errorf("Segredo ausente: %s", nome);
            ausentes.add(nome);
        }
    }
}
