package br.com.iv.repository;

import java.sql.Date;
import java.util.List;

import br.com.iv.dto.AihLaudoElegivelDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.LaudoAutorizacao;


@ApplicationScoped
public class LaudoAutorizacaoRepository implements PanacheRepository<LaudoAutorizacao> {
	@Inject
	EntityManager entityManager;
	
	public LaudoAutorizacao filtarPorBam(int query) {
		return find("bam = ?1", query).firstResult();
	}

	public List<AihLaudoElegivelDto> listarElegiveisParaAih(String competencia, String unidade, String busca) {
		return listarElegiveisParaAih(competencia, unidade, busca, false);
	}

	/**
	 * Com somenteSemPreAih, devolve apenas os laudos que ainda nao viraram pre-AIH.
	 *
	 * O "limit 100" no fim desta consulta e o teto de uma rodada. Sem este filtro, a
	 * segunda chamada devolvia exatamente os mesmos 100 laudos do topo (a ordem e fixa
	 * por datainternacao), entao repetir a acao reprocessava sempre as mesmas contas e
	 * nunca alcancava o resto da competencia - numa competencia com 475 laudos, so as
	 * internacoes mais recentes eram acessiveis pela tela. Com o filtro, cada rodada come
	 * 100 e a seguinte pega as proximas, ate esgotar.
	 *
	 * O dropdown de laudos continua chamando a versao sem filtro: la o operador precisa
	 * ver tambem as que ja tem pre-AIH, marcadas como "[Pre-AIH criada]".
	 */
	public List<AihLaudoElegivelDto> listarElegiveisParaAih(String competencia, String unidade, String busca,
			boolean somenteSemPreAih) {
		StringBuilder sql = new StringBuilder("""
				select
					l.idlaudo,
					i.idninternado,
					l.bam,
					coalesce(p.nome, '') as paciente,
					coalesce(i.prontuario, '') as prontuario,
					coalesce(i.unidade, l.unidade, '') as unidade,
					coalesce(i.cid, l.cidprincipal, '') as cid,
					coalesce(l.procedimentosolicitado, '') as procedimento,
					coalesce(l.nainternacao, '') as numero_autorizacao,
					i.datainternacao,
					l.dataautorizacao,
					case when fa.idaih is null then false else true end as pre_aih_criada
				from laudoautorizacao l
				join internacao i on cast(nullif(regexp_replace(i.bam, '\\D', '', 'g'), '') as integer) = l.bam
				left join pacientes p on p.idpaciente = i.iddopaciente
				left join faturamento_aih fa on fa.idinternacao = i.idninternado
				where 1 = 1
				""");
		if (somenteSemPreAih) {
			sql.append(" and fa.idaih is null");
		}
		if (competencia != null && !competencia.isBlank()) {
			sql.append(" and to_char(i.datainternacao, 'YYYYMM') = :competencia");
		}
		if (unidade != null && !unidade.isBlank()) {
			sql.append(" and upper(coalesce(i.unidade, l.unidade, '')) = :unidade");
		}
		if (busca != null && !busca.isBlank()) {
			sql.append("""
				 and (
					upper(coalesce(p.nome, '')) like :busca
					or cast(l.bam as text) like :busca
					or upper(coalesce(i.prontuario, '')) like :busca
					or upper(coalesce(l.nainternacao, '')) like :busca
				 )
				 """);
		}
		sql.append(" order by i.datainternacao desc, l.idlaudo desc limit 100");

		var query = entityManager.createNativeQuery(sql.toString());
		if (competencia != null && !competencia.isBlank()) {
			query.setParameter("competencia", competencia);
		}
		if (unidade != null && !unidade.isBlank()) {
			query.setParameter("unidade", unidade.trim().toUpperCase());
		}
		if (busca != null && !busca.isBlank()) {
			query.setParameter("busca", "%" + busca.trim().toUpperCase() + "%");
		}
		@SuppressWarnings("unchecked")
		List<Object[]> linhas = query.getResultList();
		return linhas.stream().map(this::mapearLaudoElegivel).toList();
	}

	// Mesma regra de elegibilidade de listarIdsInternacaoElegiveisParaAih (precisa existir
	// laudo de autorizacao vinculado pelo BAM), mas para uma unica internacao - usado no
	// gatilho automatico de alta, que nao tem competencia para filtrar.
	public boolean existeLaudoAutorizacaoParaInternacao(int idinternacao) {
		String sql = """
				select count(1)
				from laudoautorizacao l
				join internacao i on cast(nullif(regexp_replace(i.bam, '\\D', '', 'g'), '') as integer) = l.bam
				where i.idninternado = :idinternacao
				""";
		Number total = (Number) entityManager.createNativeQuery(sql)
				.setParameter("idinternacao", idinternacao)
				.getSingleResult();
		return total != null && total.longValue() > 0;
	}

	public Integer buscarIdInternacaoPorLaudo(int idlaudo, String unidade) {
		StringBuilder sql = new StringBuilder("""
				select i.idninternado
				from laudoautorizacao l
				join internacao i on cast(nullif(regexp_replace(i.bam, '\\D', '', 'g'), '') as integer) = l.bam
				where l.idlaudo = :idlaudo
				""");
		if (unidade != null && !unidade.isBlank()) {
			sql.append(" and upper(coalesce(i.unidade, l.unidade, '')) = :unidade");
		}
		sql.append(" order by i.datainternacao desc, i.idninternado desc limit 1");
		var query = entityManager.createNativeQuery(sql.toString())
				.setParameter("idlaudo", idlaudo);
		if (unidade != null && !unidade.isBlank()) {
			query.setParameter("unidade", unidade.trim().toUpperCase());
		}
		@SuppressWarnings("unchecked")
		List<Number> ids = query.getResultList();
		return ids.isEmpty() ? null : ids.get(0).intValue();
	}

	// So as que ainda nao tem pre-AIH: e o que permite a acao em lote avancar a cada
	// rodada em vez de reprocessar sempre as mesmas 100 do topo.
	public List<Integer> listarIdsInternacaoElegiveisParaAih(String competencia, String unidade, String busca) {
		return listarElegiveisParaAih(competencia, unidade, busca, true).stream()
				.map(AihLaudoElegivelDto::idinternacao)
				.distinct()
				.toList();
	}

	private AihLaudoElegivelDto mapearLaudoElegivel(Object[] row) {
		return new AihLaudoElegivelDto(
				numero(row[0]).intValue(),
				numero(row[1]).intValue(),
				numero(row[2]).intValue(),
				texto(row[3]),
				texto(row[4]),
				texto(row[5]),
				texto(row[6]),
				texto(row[7]),
				texto(row[8]),
				data(row[9]),
				data(row[10]),
				Boolean.TRUE.equals(row[11]));
	}

	private Number numero(Object valor) {
		return valor instanceof Number n ? n : 0;
	}

	private String texto(Object valor) {
		return valor == null ? "" : valor.toString();
	}

	private java.time.LocalDate data(Object valor) {
		if (valor instanceof Date d) {
			return d.toLocalDate();
		}
		if (valor instanceof java.util.Date d) {
			return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
		}
		return null;
	}

	public int encaminharTratamentoAoNir(int bam) {
		String sql = "UPDATE tratamento t "
				+ "SET tipo_alta = 'internacao', tratado = 1 "
				+ "FROM bam b "
				+ "WHERE b.tratamento_idtratamento = t.idtratamento AND b.idbam = :bam";
		return entityManager.createNativeQuery(sql)
				.setParameter("bam", bam)
				.executeUpdate();
	}

	public void sinalizarRegistroEncaminhadoAoNir(int bam) {
		String sql = "UPDATE registro r "
				+ "SET atendidopor = concat(coalesce(r.atendidopor, ''), ' ') "
				+ "FROM bam b "
				+ "WHERE b.registro_idregistro = r.idregistro AND b.idbam = :bam";
		entityManager.createNativeQuery(sql)
				.setParameter("bam", bam)
				.executeUpdate();
	}
	
	
}
