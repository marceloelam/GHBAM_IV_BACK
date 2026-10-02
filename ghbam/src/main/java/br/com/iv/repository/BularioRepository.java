package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.Bulario;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BularioRepository implements PanacheRepository<Bulario> {

	public List<Bulario> filtarBulario(String ynome) {
				
		return find("upper(nome) like ?1 or upper(principioAtivo) like ?2  or upper(principioAtivo) like ?2 "
				+ " or upper(resumo) like ?3  or upper(indicacoes) like ?4  or upper(posologia) like ?5 "
				+ " or upper(contraindicacoes) like ?6  or upper(efeitosAdversos) like ?7  or upper(interacoes) like ?8 "
				+ "  or upper(precaucoes) like ?9   or upper(fonte) like ?10   or upper(tarja) like ?11 ", "%" + ynome.toUpperCase() + "%",  
				"%" + ynome.toUpperCase() + "%", "%" + ynome.toUpperCase() + "%", "%" + ynome.toUpperCase() + "%", "%" + ynome.toUpperCase() + "%", 
				"%" + ynome.toUpperCase() + "%", "%" + ynome.toUpperCase() + "%", "%" + ynome.toUpperCase() + "%", "%" + ynome.toUpperCase() + "%", 
				"%" + ynome.toUpperCase() + "%", "%" + ynome.toUpperCase() + "%").list();
	}

}
