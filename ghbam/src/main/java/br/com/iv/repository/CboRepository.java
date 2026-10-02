package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.Cbo;


@ApplicationScoped
public class CboRepository implements PanacheRepository<Cbo> {
	
	public List<Cbo> filtarPorCbo(String query) {
	return find("upper(titulo) like ?1", "%" +query.toUpperCase() + "%").list();
}

	// Titulo exatamente igual ao cargo do servidor. O filtro por like existe para o
	// operador procurar digitando, mas para preencher CBO sozinho ele nao serve: "MEDICO"
	// casa com dezenas de titulos e escolher um seria adivinhar a ocupacao de alguem.
	public List<Cbo> filtrarPorTituloExato(String titulo) {
	return find("upper(trim(titulo)) = ?1", titulo.trim().toUpperCase()).list();
}

	public List<Cbo> filtarPorCboCodigo(String query) {
	return find("upper(codigo) like ?1", "%" +query.toUpperCase() + "%").list();
}

}
