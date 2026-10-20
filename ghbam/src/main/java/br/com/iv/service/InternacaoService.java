package br.com.iv.service;

import java.io.Serializable;
import java.text.ParseException;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.dto.InternacaoDto;
import br.com.iv.entity.Internacao;
import br.com.iv.repository.InternacaoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class InternacaoService implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(InternacaoService.class);

	// Mesmo padrao das outras automacoes do faturamento (FaturamentoAutomacaoJob): chave
	// dedicada para desligar sem deploy, caso a pre-AIH automatica na alta precise ser
	// revista em producao.
	@ConfigProperty(name = "ghbam.faturamento.automacao.pre-aih-na-alta", defaultValue = "true")
	boolean preAihNaAlta;

	@Inject
	InternacaoRepository internacaoRepository;

	@Inject
	FaturamentoAihService faturamentoAihService;

	@Inject
	ProntuarioDefinitivoService prontuarioDefinitivoService;

	// Listando todas as Internacao
	public List<Internacao> listarTodasInternacao() {
		return internacaoRepository.listAll(Sort.by("sala"));
	}

	public Internacao filtarPorUnico(String id) {
		return internacaoRepository.filtrarPorUnico(id);
	}

	// Salvar
	@Transactional
	public Internacao salvarInternacao(InternacaoDto dto) throws ParseException {

		Internacao internacao = new Internacao();
		internacao.setProcedencia(dto.getProcedencia());
		internacao.setDatainternacao(dto.getDatainternacao());
		internacao.setProntuario(dto.getProntuario());
		internacao.setTpdocumento(dto.getTpdocumento());
		if (dto.getNdocumento() == null)
			internacao.setNdocumento(internacao.getNdocumento());
		else
			internacao.setNdocumento(dto.getNdocumento());
		internacao.setBam(dto.getBam());
		internacao.setCid(dto.getCid());
		internacao.setLocal(dto.getLocal());
		internacao.setMedicodeualta(dto.getMedicodeualta());
		internacao.setMedicoqueinternou(dto.getMedicoqueinternou());
		internacao.setDatasaida(dto.getDatasaida());
		internacao.setMotivodaalta(dto.getMotivodaalta());
		internacao.setObs(dto.getObs());
		internacao.setAtendente(dto.getAtendente());
		internacao.setOrigem(dto.getOrigem());
		internacao.setUnidade(dto.getUnidade());
		internacao.setIdDoPaciente(dto.getIdDoPaciente());

		// Vale para NIR, Admissao do RN e emergencia: internado sai daqui com prontuario definitivo.
		String definitivo = prontuarioDefinitivoService.garantir(dto.getIdDoPaciente(), dto.getAtendente());
		if (definitivo != null) {
			internacao.setProntuario(definitivo);
		}

		internacao.persist();
		return internacao;

	}

	@SuppressWarnings("static-access")
	@Transactional
	public Internacao alterarInternacao(int id, InternacaoDto dto) {
		internacaoRepository.alterarInternacao(id, dto);
		if (preAihNaAlta && dto.getDatasaida() != null) {
			// Alta registrada agora: adianta a pre-AIH em vez de esperar o preparo de
			// madrugada (FaturamentoAutomacaoJob). So cria quando ja existe laudo de
			// autorizacao vinculado (criarPreAihSeElegivel) - internacao sem laudo nao vira
			// pendencia de faturamento por engano. Falha aqui nao pode impedir a alta.
			try {
				faturamentoAihService.criarPreAihSeElegivel(id, null);
			} catch (RuntimeException e) {
				LOG.warnf(e, "[ALTA-AUTO-AIH] falha ao criar pre-AIH automatica para internacao %d", id);
			}
		}
		return null;
	}
	
	@Transactional
	public Internacao restaurarinternacao(String bam) {
		 internacaoRepository.restaurarinternacao(bam);
		return null;
	}


}
