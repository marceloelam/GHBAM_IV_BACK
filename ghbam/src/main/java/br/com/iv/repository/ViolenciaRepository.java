package br.com.iv.repository;

import br.com.iv.entity.Violencia;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ViolenciaRepository implements PanacheRepository<Violencia> {
}
