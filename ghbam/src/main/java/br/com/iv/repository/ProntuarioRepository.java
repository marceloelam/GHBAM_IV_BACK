package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.Prontuario;

@ApplicationScoped
public class ProntuarioRepository implements PanacheRepository<Prontuario> {

	public List<Prontuario> filtarPorCbo(String query) {
		return find("upper(prontuario) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	       public Prontuario filtrarCodPront(String query) {
		       return find("controle = ?1", query).firstResult();
	       } 

	public List<Prontuario> filtrarCodPront2(String cod) {
		return find("controle = ?1", cod.toUpperCase()).list();
	}

}
