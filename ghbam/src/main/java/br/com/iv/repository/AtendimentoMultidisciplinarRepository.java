package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.AtendimentoMultidisciplinar;

@ApplicationScoped
public class AtendimentoMultidisciplinarRepository implements PanacheRepository<AtendimentoMultidisciplinar> {

	public List<AtendimentoMultidisciplinar> listarPorPaciente(int idpaciente) {
		return list("idpaciente = ?1 and ativo = true order by dataAtendimento desc, id desc", idpaciente);
	}

	public List<AtendimentoMultidisciplinar> listarPorIdbam(int idbam) {
		return list("idbam = ?1 and ativo = true order by dataAtendimento desc, id desc", idbam);
	}

}
