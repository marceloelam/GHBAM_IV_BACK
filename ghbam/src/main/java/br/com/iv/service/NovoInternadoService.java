package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.iv.dto.NovoInternadoDto;
import br.com.iv.entity.AssinaturaClinica;
import br.com.iv.entity.Bam;
import br.com.iv.entity.NovoInternado;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.NovoInternadoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class NovoInternadoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	NovoInternadoRepository novointernadoRepository;

	@Inject
	BamRepository bamRepository;

	@Inject
	ProntuarioDefinitivoService prontuarioDefinitivoService;

	@Inject
	LeitoOcupacaoGuard leitoOcupacaoGuard;

	@Inject
	EntityManager entityManager;

	@ConfigProperty(name = "ghbam.alta.exigir-resumo-assinado", defaultValue = "false")
	boolean exigirResumoAssinado;

	public List<NovoInternado> listarTodosOsRegistros() {
		return novointernadoRepository.listAll();
	}

	public List<NovoInternado> listarTodosOsRegistrosAtivos(String unidade) {
		return novointernadoRepository.listarTodosOsRegistrosAtivos(unidade);
	}

	public long contagem(String unidade, String quem) {
		return novointernadoRepository.contagem(unidade, quem);
	}

	public NovoInternado filtarPorUnico(int id) {
		return novointernadoRepository.filtrarPorUnico(id);
	}

	@SuppressWarnings("static-access")
	@Transactional
	public NovoInternado alterarLeito(int bam, int setor, int leito, String quem, boolean confirmarLeitoOcupado) {
		leitoOcupacaoGuard.conferir(leito, bam, confirmarLeitoOcupado, "Transferencia de leito");
		novointernadoRepository.alterarLeito(bam, setor, leito, quem);
		leitoOcupacaoGuard.encerrarReservaDoBam(bam, leito, quem);
		return null;
	}

	@SuppressWarnings("static-access")
	@Transactional
	public NovoInternado alterarAltaDoPaciente(int idbam, LocalDate acao, boolean alta, String quem, String motivo,
			int idnovo) {
		// Desligada por padrao: em 09/11/2026 so 5 de 750 altas tinham resumo assinado -
		// ligar antes de os medicos adotarem a assinatura pararia o NIR.
		if (alta) {
			conferirResumoAltaAssinado(idbam, motivo);
		}
		novointernadoRepository.alterarAltaDoPaciente(idbam, acao, alta, quem, motivo, idnovo);
		return null;
	}

	/**
	 * Alta da internacao (tela "Alta da internacao") tambem libera o leito do mesmo BAM -
	 * antes o paciente saia da internacao e continuava ocupando o leito no censo. O sentido
	 * inverso (alta do leito fecha a internacao) fica em NovoInternadoRepository.
	 */
	@Transactional
	public int liberarLeitoNaAltaDaInternacao(int idbam, LocalDate acao, String quem, String motivo) {
		String m = motivo == null ? "" : motivo.trim();
		if (m.isEmpty() || m.equals("-") || m.equalsIgnoreCase("ALTA ADM")) {
			return 0;
		}
		return entityManager.createNativeQuery(
				"UPDATE novointernado SET alta = true, datadaacao = :acao, quemdeualta = :quem, motivosaida = :motivo "
						+ "WHERE nbam = :idbam AND alta = false")
				.setParameter("acao", acao == null ? LocalDate.now() : acao)
				// Colunas: quemdeualta varchar(185), motivosaida varchar(20) - estourar derrubaria a alta.
				.setParameter("quem", quem == null ? "" : quem.length() > 185 ? quem.substring(0, 185) : quem)
				.setParameter("motivo", m.length() > 20 ? m.substring(0, 20) : m)
				.setParameter("idbam", idbam)
				.executeUpdate();
	}

	/** Usado pelas duas portas de alta (leito e internacao). So age com a chave ligada. */
	public void conferirResumoAltaAssinado(int idbam, String motivo) {
		// Obito: sempre exige o Resumo de Alta preenchido com obito e data (decisao de 09/11/2026).
		// Sem ele o sistema ficava sem a data real do obito e o caso sumia do Sumario de Obito.
		if (exigeResumoObito(motivo) && !possuiResumoAltaDeObito(idbam)) {
			throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
					.entity(Map.of(
							"erro", "Saida por obito bloqueada: o medico precisa preencher o Resumo de Alta deste BAM "
									+ "marcando obito, com data e hora do obito. Depois registre a saida novamente.",
							"campo", "RESUMO_ALTA_OBITO"))
					.type(MediaType.APPLICATION_JSON)
					.build());
		}
		// Sem Resumo de Alta preenchido nao ha alta (decisao de 09/11/2026), com ou sem a chave de assinatura.
		if (exigeResumoAlta(motivo) && !possuiResumoAltaPreenchido(idbam)) {
			throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
					.entity(Map.of(
							"erro", "Alta bloqueada: o Resumo de Alta deste paciente ainda nao foi preenchido pelo medico.",
							"campo", "RESUMO_ALTA_PREENCHIDO"))
					.type(MediaType.APPLICATION_JSON)
					.build());
		}
		if (exigirResumoAssinado && exigeResumoAlta(motivo) && !possuiResumoAltaAssinado(idbam)) {
			throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
					.entity(Map.of(
							"erro", "Alta bloqueada: o Resumo de Alta deste paciente ainda nao foi assinado pelo medico.",
							"campo", "RESUMO_ALTA"))
					.type(MediaType.APPLICATION_JSON)
					.build());
		}
	}

	// Alta final exige Resumo de Alta assinado. Ficam de fora: obito e evasao (o medico pode
	// nao estar presente na hora; o resumo vem depois), ALTA ADM (nao encerra a internacao)
	// e "-" (opcao INTERNADO, que nao e alta).
	static boolean exigeResumoAlta(String motivo) {
		String m = motivo == null ? "" : motivo.trim().toUpperCase();
		return !m.isEmpty() && !m.equals("-") && !m.equals("ALTA ADM")
				&& !m.startsWith("OBITO") && !m.contains("EVASAO");
	}

	// Natimorto fica de fora: nao ha internacao do RN com Resumo de Alta.
	static boolean exigeResumoObito(String motivo) {
		String m = motivo == null ? "" : java.text.Normalizer.normalize(motivo.trim().toUpperCase(), java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return m.startsWith("OBITO") && !m.contains("NATIMORTO");
	}

	private boolean possuiResumoAltaDeObito(int idbam) {
		Number total = (Number) entityManager.createNativeQuery(
				"SELECT COUNT(*) FROM resumodealta WHERE bam = CAST(:bam AS text) AND obito = true "
						+ "AND dataobito IS NOT NULL AND COALESCE(status, 'ATIVO') <> 'CANCELADO'")
				.setParameter("bam", idbam)
				.getSingleResult();
		return total != null && total.longValue() > 0;
	}

	public boolean possuiResumoAltaPreenchido(int idbam) {
		Number total = (Number) entityManager.createNativeQuery(
				"SELECT COUNT(*) FROM resumodealta WHERE bam = CAST(:bam AS text) AND COALESCE(status, 'ATIVO') <> 'CANCELADO'")
				.setParameter("bam", idbam)
				.getSingleResult();
		return total != null && total.longValue() > 0;
	}

	/**
	 * ALTA ADM pela tela "Alta da internacao": mesmo efeito da alta ADM do leito (registro em
	 * novointernadoadm + altaadmnir); a internacao e o leito continuam abertos.
	 */
	@Transactional
	public void registrarAltaAdmPelaInternacao(int idbam, LocalDate acao, String quem) {
		List<?> ids = entityManager.createNativeQuery(
				"SELECT idninternado FROM novointernado WHERE nbam = :idbam AND alta = false ORDER BY idninternado DESC LIMIT 1")
				.setParameter("idbam", idbam).getResultList();
		if (ids.isEmpty()) {
			throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
					.entity(Map.of("erro", "ALTA ADM e registrada no leito, e este paciente nao esta alocado em leito.",
							"campo", "LEITO"))
					.type(MediaType.APPLICATION_JSON)
					.build());
		}
		novointernadoRepository.alterarAltaDoPaciente(idbam, acao == null ? LocalDate.now() : acao, true,
				quem == null ? "" : quem, "ALTA ADM", ((Number) ids.get(0)).intValue());
	}

	private boolean possuiResumoAltaAssinado(int idbam) {
		return AssinaturaClinica.count("tipoDocumento = ?1 and idbam = ?2 and upper(status) in ('ASSINADO', 'ADENDO')",
				"RESUMO_ALTA", idbam) > 0;
	}

	@SuppressWarnings("static-access")
	@Transactional
	public NovoInternado deletarAlocacao(int idbam) {
		novointernadoRepository.deletarAlocacao(idbam);
		return null;
	}

	// Salvar
	@Transactional
	public NovoInternado salvarNovoInternado(NovoInternadoDto dto, boolean confirmarLeitoOcupado) {
		// Fora do try abaixo, que engole excecoes: a recusa precisa chegar a tela.
		int idleito = dto.getLocal() == null ? 0 : dto.getLocal().getIdsetorlocalcenso();
		if (!dto.isAlta()) {
			leitoOcupacaoGuard.conferir(idleito, dto.getNbam(), confirmarLeitoOcupado, "Alocacao no censo");
		}
		try {
			NovoInternado novoInternado = new NovoInternado();
			Bam numeroDoBam = dto.getBam();

			novoInternado.setSetor(dto.getSetor());
			novoInternado.setLocal(dto.getLocal());
			novoInternado.setProntuario(dto.getProntuario());
			novoInternado.setInternado(dto.getInternado());
			// if (dto.getNdocumento() == null)
			// novoInternado.setNdocumento(novoInternado.getNdocumento());
			// else
			// novoInternado.setNdocumento(dto.getNdocumento());
			novoInternado.setBam(dto.getBam());
			novoInternado.setPacientes(dto.getPacientes());
			novoInternado.setNbam(numeroDoBam.getIdbam());
			novoInternado.setEspecialidade(dto.getEspecialidade());
			novoInternado.setDiagnostico(dto.getDiagnostico());
			novoInternado.setDiainternacao(dto.getDiainternacao());
			novoInternado.setTempoDias(dto.getTempoDias());
			novoInternado.setDatainsercaoser(dto.getDatainsercaoser());
			novoInternado.setDatadaacao(dto.getDatadaacao());
			novoInternado.setLeitoocupado(dto.getLeitoocupado());
			novoInternado.setAgente(dto.getAgente());
			novoInternado.setTipo(dto.getTipo());
			novoInternado.setMotivosaida(dto.getMotivosaida());
			novoInternado.setPendencia(dto.getPendencia());
			novoInternado.setUnidade(dto.getUnidade());
			novoInternado.setAlta(dto.isAlta());
			novoInternado.setNbam(dto.getNbam());
			
			novoInternado.setBam(dto.getBam());
			novoInternado.setTipo_alocacao(dto.getTipo_alocacao());
			novoInternado.setQuemalocou(dto.getQuemalocou());
			novoInternado.setQuemdeualta(dto.getQuemdeualta());
			novoInternado.setQuemexcluiu(dto.getQuemexcluiu());
			novoInternado.setQuemleitoalterou(dto.getQuemleitoalterou());

			// Admissao do RN aloca direto no censo: o leito ja sai com o prontuario definitivo.
			if (dto.getPacientes() != null) {
				String definitivo = prontuarioDefinitivoService.garantir(dto.getPacientes().getIdpaciente(), dto.getQuemalocou());
				if (definitivo != null) {
					novoInternado.setProntuario(definitivo);
				}
			}

			novoInternado.persist();
			bamRepository.atualizarMarcadorTransferencia(numeroDoBam.getIdbam(), false);
			if (!dto.isAlta()) {
				leitoOcupacaoGuard.encerrarReservaDoBam(dto.getNbam(), idleito, dto.getQuemalocou());
			}
			return novoInternado;

		} catch (Exception e) {

		}
		return null;

	}

	public List<NovoInternado> listarBamUnidade(String bam, String unidade) {
		return novointernadoRepository.listarBamUnidade(bam,unidade);
		}

	@Transactional
	@SuppressWarnings("unchecked")
	public boolean cancelarAltaNoCenso(int bam, boolean confirmarLeitoOcupado) {
		List<Number> leitos = entityManager.createNativeQuery(
				"SELECT local_idsetorlocalcenso FROM novointernado WHERE nbam = :bam AND alta = true ORDER BY idninternado DESC LIMIT 1")
				.setParameter("bam", bam)
				.getResultList();
		for (Number leito : leitos) {
			leitoOcupacaoGuard.conferir(leito.intValue(), bam, confirmarLeitoOcupado, "Cancelamento de alta no censo");
		}
		return novointernadoRepository.cancelarAltaNoCenso(bam) > 0;
	}

	/*
	 * @SuppressWarnings("static-access")
	 * 
	 * @Transactional public NovoInternado alterarNovoInternado(int id,
	 * NovoInternadoDto dto) { NovoInternadoRepository.alterarNovoInternado(id,
	 * dto); return null; }
	 */

}
