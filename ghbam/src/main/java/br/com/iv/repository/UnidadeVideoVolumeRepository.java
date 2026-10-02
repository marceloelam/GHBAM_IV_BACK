package br.com.iv.repository;

import java.util.Optional;

import br.com.iv.entity.UnidadeVideoVolume;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UnidadeVideoVolumeRepository implements PanacheRepository<UnidadeVideoVolume> {

	public Optional<UnidadeVideoVolume> buscarPorSiglaETv(String sigla, int tv) {
		return find("sigla = ?1 and tv = ?2", sigla, tv).firstResultOptional();
	}
}
