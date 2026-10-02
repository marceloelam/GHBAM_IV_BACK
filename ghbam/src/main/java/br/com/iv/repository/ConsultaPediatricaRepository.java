package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ConsultaPediatrica;

@ApplicationScoped
public class ConsultaPediatricaRepository implements PanacheRepository<ConsultaPediatrica> {

	public List<ConsultaPediatrica> listarPorPaciente(int idpaciente) {
		return list("idpaciente = ?1 order by dataConsulta desc", idpaciente);
	}

	public List<ConsultaPediatrica> listarPorProntuario(String prontuario) {
		return list("prontuario = ?1 order by dataConsulta desc", prontuario);
	}

	public List<ConsultaPediatrica> listarPorIdbam(int idbam) {
		return list("idbam = ?1 order by dataConsulta desc", idbam);
	}

	public ConsultaPediatrica buscarUltimaPorPaciente(int idpaciente) {
		return find("idpaciente = ?1 order by dataConsulta desc", idpaciente).firstResult();
	}

}
