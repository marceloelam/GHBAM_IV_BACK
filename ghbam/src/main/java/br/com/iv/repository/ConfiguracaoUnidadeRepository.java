package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ConfiguracaoUnidade;

@ApplicationScoped
public class ConfiguracaoUnidadeRepository implements PanacheRepository<ConfiguracaoUnidade> {
	
	public List<ConfiguracaoUnidade> filtarPorUnidadeConfiguracao(String query) {
		return find("hospital like ?1",   query ).list();
	}

	public List<ConfiguracaoUnidade> filtarPorUnidadeSigla(String query) {
		String siglaNormalizada = query == null ? "" : query.trim();
		return find("upper(sigla) like upper(?1)", siglaNormalizada).list();
	}
	// Municipio da unidade, pra validar que servidor so recebe unidade
	// adicional/principal do mesmo municipio (ver ServidorService).
	public String buscarMunicipioPorSigla(String sigla) {
		ConfiguracaoUnidade unidade = find("upper(sigla) = upper(?1)", sigla == null ? "" : sigla.trim()).firstResult();
		return unidade == null ? null : unidade.getMunicipioPadrao();
	}

	// Codigo do gestor/municipio da unidade, para a integracao SUS compartilhar
	// credencial entre unidades do mesmo gestor (ver IntegracaoSusCredencialService).
	public String buscarCodigoGestorPorSigla(String sigla) {
		ConfiguracaoUnidade unidade = find("upper(sigla) = upper(?1)", sigla == null ? "" : sigla.trim()).firstResult();
		return unidade == null ? null : unidade.getCodigoGestor();
	}

	public List<ConfiguracaoUnidade> listarUnidadesDistintosDoSetor() {
		// String sql = "SELECT DISTINCT on (siglaunidade) siglaunidade FROM
		// setoresunidade";
		// return entityManager.createNativeQuery(sql).getResultList();
		// String hql = "SELECT DISTINCT (u.siglaunidade) u.siglaunidade FROM Setores
		// u";
		// return entityManager.createQuery(hql, Setores.class).getResultList();
		return list("SELECT DISTINCT s.sigla FROM configurarunidade s order by s.sigla");
	}

}
