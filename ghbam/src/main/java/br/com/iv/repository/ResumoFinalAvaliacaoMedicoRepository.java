package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ResumoFinalAtendimentoMedico;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ResumoFinalAvaliacaoMedicoRepository implements PanacheRepository<ResumoFinalAtendimentoMedico> {

	@Inject
	EntityManager entityManager;
	
	@SuppressWarnings("unchecked")
	@Transactional
	public List<ResumoFinalAtendimentoMedico> listarResumoFinalMedico(String data, String unidade) {
		// LocalDate data = LocalDate.now();
		// DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		// String datas = data.format(formatador).toString();

		String sql = "select * from  finalmedico   where unidade = '" + unidade
				+ "'  and cast(dataatendimento as date)  = cast('" + data
				+ "' as date) group by idfinalmedico, medico, dataatendimento "
				+ " order by  medico,  dataatendimento desc, setor";

		return getEntityManager().createNativeQuery(sql, ResumoFinalAtendimentoMedico.class).getResultList();
	}
	
	@Transactional
	public void cancelarResumodealta(String bam) {
		String sql = "UPDATE resumodealta SET status = 'CANCELADO', cancelado_em = CURRENT_TIMESTAMP, "
				+ "cancelado_por = 'NIR', motivo_cancelamento = 'Cancelamento da alta pelo NIR' "
				+ "WHERE bam = :bam AND COALESCE(status, 'ATIVO') <> 'CANCELADO'";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
	}

}
