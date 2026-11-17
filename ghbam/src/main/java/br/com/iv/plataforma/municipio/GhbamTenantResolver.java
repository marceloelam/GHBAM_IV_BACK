package br.com.iv.plataforma.municipio;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Hibernate: tenant = codigo IBGE do municipio. So e' usado com
 * quarkus.hibernate-orm.multitenant=DATABASE; com NONE o Quarkus ignora este bean.
 */
@ApplicationScoped
@PersistenceUnitExtension
public class GhbamTenantResolver implements TenantResolver {

    @Inject
    MunicipioRoteamento roteamento;

    @Override
    public String getDefaultTenantId() {
        return roteamento.padrao();
    }

    @Override
    public String resolveTenantId() {
        return roteamento.atual();
    }
}
