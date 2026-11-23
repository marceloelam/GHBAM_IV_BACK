package br.com.iv.plataforma.municipio;

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.customized.QuarkusConnectionProvider;
import io.quarkus.hibernate.orm.runtime.tenant.TenantConnectionResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Hibernate: entrega o pool do banco do municipio (o da aplicacao para o municipio padrao). */
@ApplicationScoped
@PersistenceUnitExtension
public class GhbamTenantConnectionResolver implements TenantConnectionResolver {

    @Inject
    MunicipioRoteamento roteamento;

    @Override
    public ConnectionProvider resolve(String tenantId) {
        // O Hibernate guarda este provider por município. Prender o pool aqui deixava o município com
        // "This pool is closed" depois de o banco ser apagado/recriado (esquecerPool fecha o antigo):
        // por isso o pool é buscado a cada conexão (ensaio de 20/11/2026).
        return new PoolAtualDoMunicipio(roteamento, tenantId);
    }

    static final class PoolAtualDoMunicipio implements ConnectionProvider {
        private final MunicipioRoteamento roteamento;
        private final String tenantId;

        PoolAtualDoMunicipio(MunicipioRoteamento roteamento, String tenantId) {
            this.roteamento = roteamento;
            this.tenantId = tenantId;
        }

        private QuarkusConnectionProvider atual() {
            return new QuarkusConnectionProvider(roteamento.dataSource(tenantId));
        }

        @Override
        public java.sql.Connection getConnection() throws java.sql.SQLException {
            return roteamento.dataSource(tenantId).getConnection();
        }

        @Override
        public void closeConnection(java.sql.Connection conn) throws java.sql.SQLException {
            conn.close();
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return atual().isUnwrappableAs(unwrapType);
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            return atual().unwrap(unwrapType);
        }
    }
}
