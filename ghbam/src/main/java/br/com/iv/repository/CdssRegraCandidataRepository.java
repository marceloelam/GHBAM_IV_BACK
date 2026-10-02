package br.com.iv.repository;
import br.com.iv.entity.CdssRegraCandidata;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class CdssRegraCandidataRepository implements PanacheRepository<CdssRegraCandidata> {}
