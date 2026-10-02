package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ResumoDeAltaDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.ResumoDeAlta;
import br.com.iv.repository.ResumoDeAltaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ResumoDeAltaService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ResumoDeAltaRepository resumoDeAltaRepository;

	@Inject
	AssinaturaClinicaService assinaturaClinicaService;

	// Listando todos os log
	public List<ResumoDeAlta> listarTodosResumos() {
		return resumoDeAltaRepository.listarAtivos();
	}

	public List<ResumoDeAlta> listarCanceladosPorUnidade(String unidade, String nome, String bam, String prontuario) {
		return resumoDeAltaRepository.listarCanceladosPorUnidade(unidade, nome, bam, prontuario);
	}
	
	public ResumoDeAlta filtarPorUnicoBam(int id) {
		return resumoDeAltaRepository.filtrarPorUnicoBam(id);
	}

	// Salvar
	@Transactional
	public ResumoDeAlta salvarResumoDeAlta(ResumoDeAltaDto dto) {
		ResumoDeAlta resumoDeAlta = new ResumoDeAlta();
		resumoDeAlta.setNome(dto.getNome());
		resumoDeAlta.setProntuario(dto.getProntuario());
		resumoDeAlta.setDatainternacao(dto.getDatainternacao());
		resumoDeAlta.setDataalta(dto.getDataalta());
		resumoDeAlta.setInternacao(dto.getInternacao());
		resumoDeAlta.setObjetivo(dto.getObjetivo());
		resumoDeAlta.setClinicaalta(dto.isClinicaalta());
		resumoDeAlta.setEspecificarclicica(dto.getEspecificarclicica());
		resumoDeAlta.setDiaginicial(dto.getDiaginicial());
		resumoDeAlta.setCid(dto.getCid());
		resumoDeAlta.setDiagfinal(dto.getDiagfinal());
		resumoDeAlta.setCid2(dto.getCid2());
		resumoDeAlta.setEndoscopiadiges(dto.isEndoscopiadiges());
		resumoDeAlta.setTomografiacom(dto.isTomografiacom());
		resumoDeAlta.setUltrasono(dto.isUltrasono());
		resumoDeAlta.setSanguetot(dto.isSanguetot());
		resumoDeAlta.setObito(dto.isObito());
		resumoDeAlta.setConcentradohema(dto.isConcentradohema());
		resumoDeAlta.setConcentradounidades(dto.isConcentradounidades());
		resumoDeAlta.setPlasmahumano(dto.isPlasmahumano());
		resumoDeAlta.setPlasmahumanounida(dto.isPlasmahumanounida());
		resumoDeAlta.setPlaquetas(dto.isPlaquetas());
		resumoDeAlta.setPlaquetasunidades(dto.isPlaquetasunidades());
		resumoDeAlta.setAlbumina(dto.isAlbumina());
		resumoDeAlta.setAlbuminaunidades(dto.isAlbuminaunidades());
		resumoDeAlta.setPuncao(dto.isPuncao());
		resumoDeAlta.setNdiaspuncao(dto.getNdiaspuncao());
		resumoDeAlta.setPuncaotora(dto.isPuncaotora());
		resumoDeAlta.setEntubacao(dto.isEntubacao());
		resumoDeAlta.setCateterismo(dto.isCateterismo());
		resumoDeAlta.setNdiascateterismo(dto.getNdiascateterismo());
		resumoDeAlta.setCti(dto.isCti());
		resumoDeAlta.setNdiascti(dto.getNdiascti());
		resumoDeAlta.setNdiasentoro(dto.getNdiasentoro());
		resumoDeAlta.setProcedimentocir(dto.isProcedimentocir());
		resumoDeAlta.setCtiperiodo(dto.getCtiperiodo());
		resumoDeAlta.setInfeccao(dto.isInfeccao());
		resumoDeAlta.setMateenvia(dto.getMateenvia());
		resumoDeAlta.setCultura(dto.isCultura());
		resumoDeAlta.setGermesiso(dto.getGermesiso());
		resumoDeAlta.setAntibioticoutil(dto.getAntibioticoutil());
		resumoDeAlta.setPeriodo(dto.getPeriodo());
		resumoDeAlta.setCaracteristicapropria(dto.isCaracteristicapropria());
		resumoDeAlta.setMotivosocial(dto.isMotivosocial());
		resumoDeAlta.setIntercorrencia(dto.isIntercorrencia());
		resumoDeAlta.setDoencacronica(dto.isDoencacronica());
		resumoDeAlta.setOutros(dto.isOutros());
		resumoDeAlta.setDataobito(dto.getDataobito());
		resumoDeAlta.setHoraobito(dto.getHoraobito());
		resumoDeAlta.setSemtra(dto.isSemtra());
		resumoDeAlta.setNodecurs(dto.isNodecurs());
		resumoDeAlta.setMenos(dto.isMenos());
		resumoDeAlta.setMais(dto.isMais());
		resumoDeAlta.setCausamorte(dto.getCausamorte());
		resumoDeAlta.setComplic(dto.isComplic());
		resumoDeAlta.setEspecificarcomplicacao(dto.getEspecificarcomplicacao());
		resumoDeAlta.setTipocondicao(dto.getTipocondicao());
		resumoDeAlta.setAdministrativa(dto.isAdministrativa());
		resumoDeAlta.setDecisaomedica(dto.isDecisaomedica());
		resumoDeAlta.setApedido(dto.isApedido());
		resumoDeAlta.setIndisciplina(dto.isIndisciplina());
		resumoDeAlta.setTransparencia(dto.isTransparencia());
		resumoDeAlta.setOutro(dto.isOutro());
		resumoDeAlta.setEncaamb(dto.isEncaamb());
		resumoDeAlta.setEspecificaambula(dto.getEspecificaambula());
		resumoDeAlta.setInternado(dto.getInternado());
		resumoDeAlta.setEspecperiodo(dto.getEspecperiodo());
		resumoDeAlta.setBam(dto.getBam());
		resumoDeAlta.setSangueunida(dto.isSangueunida());
		resumoDeAlta.setNdiapuncaotora(dto.getNdiapuncaotora());
		resumoDeAlta.setClinicadealta(dto.isClinicadealta());
		resumoDeAlta.setAdmissao(dto.isAdmissao());
		resumoDeAlta.setProcedimentocirespecifi(dto.getProcedimentocirespecifi());
		resumoDeAlta.setTipocondicaotrans(dto.getTipocondicaotrans());
		resumoDeAlta.setRelato(dto.getRelato());
		resumoDeAlta.setPlanoCuidados(dto.getPlanoCuidados());
		resumoDeAlta.setCapacidadeFuncional(dto.getCapacidadeFuncional());
				
		resumoDeAlta.persist();
		return resumoDeAlta;
	}

	
	// Alterar
	@SuppressWarnings("static-access")
	@Transactional
	public void atualizarResumo(int id, ResumoDeAltaDto dto) {
		ResumoDeAlta resumoDeAlta = new ResumoDeAlta();
		Long idResumo = (long) id;
		Optional<ResumoDeAlta> resumoDeAltaOp = resumoDeAltaRepository.findByIdOptional(idResumo);
		if (resumoDeAltaOp.isEmpty()) {
			System.out.println("Resumo de alta não foi encontrado...");
		}

		resumoDeAlta = resumoDeAltaOp.get();
		bloquearSeAssinado("RESUMO_ALTA", id);
		resumoDeAlta.setNome(dto.getNome());
		resumoDeAlta.setProntuario(dto.getProntuario());
		resumoDeAlta.setDatainternacao(dto.getDatainternacao());
		resumoDeAlta.setDataalta(dto.getDataalta());
		resumoDeAlta.setInternacao(dto.getInternacao());
		resumoDeAlta.setObjetivo(dto.getObjetivo());
		resumoDeAlta.setClinicaalta(dto.isClinicaalta());
		resumoDeAlta.setEspecificarclicica(dto.getEspecificarclicica());
		resumoDeAlta.setDiaginicial(dto.getDiaginicial());
		resumoDeAlta.setCid(dto.getCid());
		resumoDeAlta.setDiagfinal(dto.getDiagfinal());
		resumoDeAlta.setCid2(dto.getCid2());
		resumoDeAlta.setEndoscopiadiges(dto.isEndoscopiadiges());
		resumoDeAlta.setTomografiacom(dto.isTomografiacom());
		resumoDeAlta.setUltrasono(dto.isUltrasono());
		resumoDeAlta.setSanguetot(dto.isSanguetot());
		resumoDeAlta.setObito(dto.isObito());
		resumoDeAlta.setConcentradohema(dto.isConcentradohema());
		resumoDeAlta.setConcentradounidades(dto.isConcentradounidades());
		resumoDeAlta.setPlasmahumano(dto.isPlasmahumano());
		resumoDeAlta.setPlasmahumanounida(dto.isPlasmahumanounida());
		resumoDeAlta.setPlaquetas(dto.isPlaquetas());
		resumoDeAlta.setPlaquetasunidades(dto.isPlaquetasunidades());
		resumoDeAlta.setAlbumina(dto.isAlbumina());
		resumoDeAlta.setAlbuminaunidades(dto.isAlbuminaunidades());
		resumoDeAlta.setPuncao(dto.isPuncao());
		resumoDeAlta.setNdiaspuncao(dto.getNdiaspuncao());
		resumoDeAlta.setPuncaotora(dto.isPuncaotora());
		resumoDeAlta.setEntubacao(dto.isEntubacao());
		resumoDeAlta.setCateterismo(dto.isCateterismo());
		resumoDeAlta.setNdiascateterismo(dto.getNdiascateterismo());
		resumoDeAlta.setCti(dto.isCti());
		resumoDeAlta.setNdiascti(dto.getNdiascti());
		resumoDeAlta.setNdiasentoro(dto.getNdiasentoro());
		resumoDeAlta.setProcedimentocir(dto.isProcedimentocir());
		resumoDeAlta.setCtiperiodo(dto.getCtiperiodo());
		resumoDeAlta.setInfeccao(dto.isInfeccao());
		resumoDeAlta.setMateenvia(dto.getMateenvia());
		resumoDeAlta.setCultura(dto.isCultura());
		resumoDeAlta.setGermesiso(dto.getGermesiso());
		resumoDeAlta.setAntibioticoutil(dto.getAntibioticoutil());
		resumoDeAlta.setPeriodo(dto.getPeriodo());
		resumoDeAlta.setCaracteristicapropria(dto.isCaracteristicapropria());
		resumoDeAlta.setMotivosocial(dto.isMotivosocial());
		resumoDeAlta.setIntercorrencia(dto.isIntercorrencia());
		resumoDeAlta.setDoencacronica(dto.isDoencacronica());
		resumoDeAlta.setOutros(dto.isOutros());
		resumoDeAlta.setDataobito(dto.getDataobito());
		resumoDeAlta.setHoraobito(dto.getHoraobito());
		resumoDeAlta.setSemtra(dto.isSemtra());
		resumoDeAlta.setNodecurs(dto.isNodecurs());
		resumoDeAlta.setMenos(dto.isMenos());
		resumoDeAlta.setMais(dto.isMais());
		resumoDeAlta.setCausamorte(dto.getCausamorte());
		resumoDeAlta.setComplic(dto.isComplic());
		resumoDeAlta.setEspecificarcomplicacao(dto.getEspecificarcomplicacao());
		resumoDeAlta.setTipocondicao(dto.getTipocondicao());
		resumoDeAlta.setAdministrativa(dto.isAdministrativa());
		resumoDeAlta.setDecisaomedica(dto.isDecisaomedica());
		resumoDeAlta.setApedido(dto.isApedido());
		resumoDeAlta.setIndisciplina(dto.isIndisciplina());
		resumoDeAlta.setTransparencia(dto.isTransparencia());
		resumoDeAlta.setOutro(dto.isOutro());
		resumoDeAlta.setEncaamb(dto.isEncaamb());
		resumoDeAlta.setEspecificaambula(dto.getEspecificaambula());
		resumoDeAlta.setInternado(dto.getInternado());
		resumoDeAlta.setEspecperiodo(dto.getEspecperiodo());
		resumoDeAlta.setBam(dto.getBam());
		resumoDeAlta.setSangueunida(dto.isSangueunida());
		resumoDeAlta.setNdiapuncaotora(dto.getNdiapuncaotora());
		resumoDeAlta.setClinicadealta(dto.isClinicadealta());
		resumoDeAlta.setAdmissao(dto.isAdmissao());
		resumoDeAlta.setProcedimentocirespecifi(dto.getProcedimentocirespecifi());
		resumoDeAlta.setTipocondicaotrans(dto.getTipocondicaotrans());
		resumoDeAlta.setRelato(dto.getRelato());
		resumoDeAlta.setPlanoCuidados(dto.getPlanoCuidados());
		resumoDeAlta.setCapacidadeFuncional(dto.getCapacidadeFuncional());
		
		resumoDeAlta.persist();
		
	}

	private void bloquearSeAssinado(String tipoDocumento, Integer iddocumento) {
		if (assinaturaClinicaService.bloqueadoPorDocumento(tipoDocumento, iddocumento)) {
			throw new WebApplicationException(
					"Documento ja assinado. Registre um adendo em vez de alterar o conteudo original.",
					Response.Status.CONFLICT);
		}
	}

}
