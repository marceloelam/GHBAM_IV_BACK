package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.Tratamento;
import br.com.iv.service.AltaadmnirService;

@ApplicationScoped
public class TratamentoRepository implements PanacheRepository<Tratamento> {
	@Inject
	EntityManager entityManager;

	public Tratamento filtrarPorId(int id) {
		return find("idtratamento = ?1", id).firstResult();
	}

	public Tratamento filtrarPorControle(String controle) {
		return find("controle = ?1", controle).firstResult();
	}

	public List<Tratamento> filtarPorControle(String query) {
		return find("controle = ?1", query).list();
	}

	@Transactional
	public void finalizarBam(int idtratamento) {
		String sql = "UPDATE tratamento set tratado = 1 where idtratamento = :idtratamento";
		entityManager.createNativeQuery(sql).setParameter("idtratamento", idtratamento).executeUpdate();
	}

	@Transactional
	public void finalizarAtendimentoMedico(int idtratamento, String alta, String op, String usuario) {
		try {
			String sql = "";

			if (op.equals("0")) {
				if (alta.equals("nirfinalizou")) {
					sql = "UPDATE tratamento SET tratado = 1, tipo_alta = :alta, finalizado_em = COALESCE(tratamento.finalizado_em, :agora) FROM bam WHERE tratamento.idtratamento = bam.tratamento_idtratamento AND bam.idbam = :idtratamento";
				} else {
					sql = "UPDATE tratamento SET tratado = 1, tipo_alta = :alta, finalizado_em = COALESCE(tratamento.finalizado_em, :agora) WHERE idtratamento = :idtratamento";
				}
			}
			if (alta.equals("internacao") && op.equals("1"))
				sql = "UPDATE tratamento SET tipo_alta = 'internacao', tratado = 1, finalizado_em = COALESCE(finalizado_em, :agora) WHERE idtratamento = :idtratamento";

			if (alta.equals("observacao") && op.equals("1")) {
				sql = "UPDATE tratamento SET tipo_alta = 'obs', tratado = 1, finalizado_em = COALESCE(finalizado_em, :agora) WHERE idtratamento = :idtratamento";
			}

			// Suporte para alta hospitalar quando op == 1
			if (alta.equals("altahospitalar") && op.equals("1")) {
				sql = "UPDATE tratamento SET tipo_alta = 'altahospitalar', tratado = 1, finalizado_em = COALESCE(finalizado_em, :agora) WHERE idtratamento = :idtratamento";
			}

			if (sql.isBlank()) {
				throw new IllegalArgumentException("Operação de alta inválida: alta=" + alta + ", op=" + op);
			}

			var query = entityManager.createNativeQuery(sql);

			if (op.equals("0")) {
				query.setParameter("alta", alta).setParameter("idtratamento", idtratamento).setParameter("agora", java.time.LocalDateTime.now());
			} else {
				query.setParameter("idtratamento", idtratamento).setParameter("agora", java.time.LocalDateTime.now());
			}

			query.executeUpdate();
			
		} catch (Exception e) {
			throw new RuntimeException("Erro ao finalizar atendimento médico: " + e.getMessage(), e);
		}
	}

	@Transactional
	public void atualizarTipoAlta(int idtratamento) {
		String sql = "UPDATE tratamento set tipo_alta = 'internacao' where idtratamento = :idtratamento";
		entityManager.createNativeQuery(sql).setParameter("idtratamento", idtratamento).executeUpdate();
	}

	@Transactional
	public void finalizarBamIdPaciente(int idtratamentoidpaciente) {
		//String sql = "UPDATE tratamento set tratado = 1 where idtratamento = :idtratamento";
		String sql = "UPDATE tratamento set tratado = 1 where idtratamento in (select tratamento_idtratamento from bam where paciente_idpaciente =  :idtratamentoidpaciente)";
		
		
		
		entityManager.createNativeQuery(sql).setParameter("idtratamentoidpaciente", idtratamentoidpaciente).executeUpdate();
		
	}

}
