package br.gov.secure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class DataSourceProxyProducer {

    @Inject
    AgroalDataSource agroalDataSource;

    public DataSource produceDataSource() {
        return agroalDataSource;
    }
}
