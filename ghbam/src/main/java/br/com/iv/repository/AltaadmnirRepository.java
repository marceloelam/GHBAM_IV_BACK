package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.Altaadmnir;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class AltaadmnirRepository implements PanacheRepository<Altaadmnir> {
	
	public List<Altaadmnir> filtarPorAltaadmnir(String query) {
	return find("bam = ?1", query).list();
}

}
