package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ProcedimentosSus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class ProcedimentosSusRepository implements PanacheRepository<ProcedimentosSus> {
	@Inject
	EntityManager entityManager;

	public List<ProcedimentosSus> filtarPorProcedimentos(String query) {
		return find("descricao like ?1 order by descricao", "%" + query + "%").list();
	}

	 
	public ProcedimentosSus filtrarPorUnicoProcedimentoSus(String procedimento) {
	    List<ProcedimentosSus> result = entityManager.createQuery(
	            "SELECT p FROM ProcedimentosSus p WHERE CONCAT(p.pa_id, p.pa_dv) = :codigo",
	            ProcedimentosSus.class)
	        .setParameter("codigo", procedimento)
	        .setMaxResults(1)
	        .getResultList();

	    return result.isEmpty() ? null : result.get(0);
	}


	// Mesma busca por codigo, mas ciente da competencia - e a que o motor de faturamento
	// usa.
	//
	// O problema que ela resolve: filtrarPorUnicoProcedimentoSus nao ordena nem filtra
	// por pa_cmp e devolve "a primeira que o banco entregar". Com varias competencias
	// SIGTAP instaladas (a base tem de 2020 a 2026), a producao de hoje era classificada
	// com o pa_tpreg de 2020 - e procedimento que hoje e BPA-I virava NAO_FATURAVEL, isto
	// e, deixava de faturar. Os campos novos (tp_sexo, faixa etaria) tambem so existem nas
	// competencias importadas depois da V139, entao a escolha errada desligava as
	// validacoes novas em silencio.
	//
	// Ordem de preferencia: a competencia exata da producao; senao a mais recente
	// anterior a ela (tabela vigente na epoca, que e a regra do SUS); senao a mais recente
	// que existir - melhor classificar por uma tabela futura do que por uma de 2020.
	public ProcedimentosSus filtrarPorProcedimentoNaCompetencia(String procedimento, String competencia) {
		List<ProcedimentosSus> encontrados = entityManager.createQuery(
				"SELECT p FROM ProcedimentosSus p WHERE CONCAT(p.pa_id, p.pa_dv) = :codigo ORDER BY p.pa_cmp DESC",
				ProcedimentosSus.class)
			.setParameter("codigo", procedimento)
			.getResultList();
		return escolherPorCompetencia(encontrados, competencia);
	}

	// Separado da consulta para poder ser testado sem banco. Recebe a lista ja ordenada
	// por competencia decrescente.
	static ProcedimentosSus escolherPorCompetencia(List<ProcedimentosSus> ordenadosDesc, String competencia) {
		if (ordenadosDesc == null || ordenadosDesc.isEmpty()) {
			return null;
		}
		String alvo = competencia == null ? "" : competencia.trim();
		if (!alvo.isBlank()) {
			for (ProcedimentosSus candidato : ordenadosDesc) {
				if (alvo.equals(texto(candidato.getPa_cmp()))) {
					return candidato;
				}
			}
			for (ProcedimentosSus candidato : ordenadosDesc) {
				String cmp = texto(candidato.getPa_cmp());
				if (!cmp.isBlank() && cmp.compareTo(alvo) < 0) {
					return candidato;
				}
			}
		}
		return ordenadosDesc.get(0);
	}

	private static String texto(String valor) {
		return valor == null ? "" : valor.trim();
	}
	public ProcedimentosSus filtrarPorPaIdDv(String paId, String paDv) {
	    List<ProcedimentosSus> result = entityManager.createQuery(
	            "SELECT p FROM ProcedimentosSus p WHERE p.pa_id = :paId AND p.pa_dv = :paDv",
	            ProcedimentosSus.class)
	        .setParameter("paId", paId)
	        .setParameter("paDv", paDv)
	        .setMaxResults(1)
	        .getResultList();

	    return result.isEmpty() ? null : result.get(0);
	}

	@SuppressWarnings("unchecked")
	public List<ProcedimentosSus> pesquisarValores(String termo, int limite) {
		String filtro = termo == null ? "" : termo.trim().toUpperCase();
		if (limite <= 0 || limite > 100) {
			limite = 50;
		}

		return getEntityManager()
				.createNativeQuery("""
						with rx as (
						    select distinct on (codprocedimento)
						        codprocedimento,
						        descricao
						    from rxprocedimentos
						    where codprocedimento is not null
						    order by codprocedimento, descricao
						),
						base as (
						    select
						        sp.idprocedimentossus,
						        sp.pa_cmp,
						        sp.pa_id,
						        sp.pa_dv,
						        sp.pa_dc,
						        sp.pa_sa,
						        sp.pa_sp,
						        sp.pa_sh,
						        sp.pa_eletiva,
						        sp.pa_tpreg,
						        sp.tp_sexo,
						        sp.vl_idade_minima,
						        sp.vl_idade_maxima,
						        sp.qt_maxima_execucao,
						        sp.tp_complexidade,
						        sp.co_financiamento,
						        row_number() over (partition by sp.pa_id, sp.pa_dv order by sp.pa_cmp desc) as rn,
						        case when rx.codprocedimento is null then 1 else 0 end as prioridade_rx,
						        coalesce(rx.descricao, sp.pa_dc) as descricao_ordem
						    from s_proced sp
						    left join rx on rx.codprocedimento = concat(sp.pa_id, sp.pa_dv)
						    where (:filtro = ''
						        or upper(sp.pa_dc) like concat('%', :filtro, '%')
						        or upper(rx.descricao) like concat('%', :filtro, '%')
						        or sp.pa_id like concat('%', :filtro, '%')
						        or concat(sp.pa_id, sp.pa_dv) like concat('%', :filtro, '%'))
						)
						select
						    idprocedimentossus,
						    pa_cmp,
						    pa_id,
						    pa_dv,
						    pa_dc,
						    pa_sa,
						    pa_sp,
						    pa_sh,
						    pa_eletiva,
						    pa_tpreg,
						    tp_sexo,
						    vl_idade_minima,
						    vl_idade_maxima,
						    qt_maxima_execucao,
						    tp_complexidade,
						    co_financiamento
						from base
						where rn = 1
						order by prioridade_rx, descricao_ordem
						limit :limite
						""", ProcedimentosSus.class)
				.setParameter("filtro", filtro)
				.setParameter("limite", limite)
				.getResultList();
	}


	    public Object buscarNoRxProcedimentos(String procedimento) {
	        @SuppressWarnings("unchecked")
			List<Object[]> result = entityManager.createNativeQuery(
	                "SELECT * FROM rxprocedimentos WHERE codprocedimento = :codigo")
	            .setParameter("codigo", procedimento)
	            .setMaxResults(1)
	            .getResultList();

	        return result.isEmpty() ? null : result.get(0);
	    }
	
	
	
	@SuppressWarnings("unchecked")
	public List<ProcedimentosSus> procediment(String texto, String cid, String cbo) {
		String sql = "select distinct on (pa_id) pa_id as pa_id_unico, s_proced.* from s_proced where UPPER(pa_dc) like '%" + texto
				+ "%' and pa_id in (select pacid_pa from s_pacid where pacid_cid in (select cd_cod from s_cid where UPPER(cd_cod) like '%"
				+ cid + "%')) and pa_id in (select pacbo_pa from s_pacbo where pacbo_cbo = '" + cbo + "' )";

		return getEntityManager().createNativeQuery(sql, ProcedimentosSus.class).getResultList();
	}

	/**
	 * Busca livre por descricao, usada quando ainda nao ha CID escolhido
	 * (agendamento cirurgico sem diagnostico fechado).
	 *
	 * Parametrizada: o termo vem digitado pelo operador e concatena-lo no SQL
	 * abriria injecao na tabela do SIGTAP. O LIMIT existe porque a busca varre
	 * s_proced inteira e a tela mostra so as primeiras linhas.
	 */
	@SuppressWarnings("unchecked")
	public List<ProcedimentosSus> procediment2(String texto) {
		String sql = "select distinct on (pa_id) pa_id as pa_id_unico, s_proced.* from s_proced "
				+ "where UPPER(pa_dc) like :texto order by pa_id limit 200";

		return getEntityManager().createNativeQuery(sql, ProcedimentosSus.class)
				.setParameter("texto", "%" + (texto == null ? "" : texto.toUpperCase()) + "%")
				.getResultList();
	}

	
	

	/**
	 * CIDs compativeis com um procedimento, pela tabela de relacionamento do SIGTAP.
	 *
	 * Caminho inverso de procediment(): ali o CID filtra procedimentos, aqui o
	 * procedimento ja escolhido (na lista de espera) restringe os CIDs validos para
	 * o agendamento. Parametrizada porque o codigo chega da tela.
	 */
	@SuppressWarnings("unchecked")
	public List<Object[]> cidsDoProcedimento(String codigoCompleto, String codigoSemDv) {
		// s_pacid ora guarda o codigo com o digito verificador, ora sem: consultar as
		// duas formas evita depender de qual carga do SIGTAP alimentou a tabela.
		String sql = "select c.cd_cod, c.cd_descr from s_cid c "
				+ "where c.cd_cod in (select pacid_cid from s_pacid where pacid_pa in (:completo, :semDv)) "
				+ "order by c.cd_cod";

		return getEntityManager().createNativeQuery(sql)
				.setParameter("completo", codigoCompleto == null ? "" : codigoCompleto)
				.setParameter("semDv", codigoSemDv == null ? "" : codigoSemDv)
				.getResultList();
	}
}
