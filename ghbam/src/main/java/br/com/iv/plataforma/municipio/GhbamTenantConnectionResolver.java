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
        return new QuarkusConnectionProvider(roteamento.dataSource(tenantId));
    }
}
