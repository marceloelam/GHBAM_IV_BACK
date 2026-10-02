package br.com.iv.repository;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.AnamneseInternacao;


@ApplicationScoped
public class AnamneseInternacaoRepository implements PanacheRepository<AnamneseInternacao> {
	
	public AnamneseInternacao filtrarPorUnicoAnamneseInternacao(int query) {
		return find("idanamneseinternacao = ?1", query).firstResult();
	}

	public AnamneseInternacao filtrarPorUnicoAnamneseInternacaoPeloBam(int query) {
		return find("nbam = ?1", query).firstResult();
	}

}
