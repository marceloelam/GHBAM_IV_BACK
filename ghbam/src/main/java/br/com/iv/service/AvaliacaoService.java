package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.iv.dto.AvaliacaoDto;
import br.com.iv.entity.Avaliacao;
import br.com.iv.repository.AvaliacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class AvaliacaoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	AvaliacaoRepository avaliacaoRepository;

	public List<Avaliacao> listarTodosOsAvaliacaos() {
		return avaliacaoRepository.listAll();
	}

	public Avaliacao listarPorID(int id) {
		return avaliacaoRepository.filtrarPorId(id);
	}

	public Avaliacao listarPorControle(String controle) {
		return avaliacaoRepository.filtrarPorControle(controle);
	}

	public Avaliacao ultimaPorPaciente(int idpaciente) {
		return avaliacaoRepository.ultimaPorPaciente(idpaciente);
	}

	@Transactional
	public List<Avaliacao> listarPorClassificacao(String unidade, String dataRequerida) {
		return avaliacaoRepository.classificacaoDeRisco(unidade, dataRequerida);
	}

	@Transactional
	public Map<String, Map<String, Long>> listarClassificacaoDeRisco(String unidade, String dataRequerida) {
		return avaliacaoRepository.listarClassificacaoDeRiscoCompleta(unidade, dataRequerida);
	}

	@Transactional
	public int atualizarAnamnese(int idavaliacao, String texto) {
		return avaliacaoRepository.atualizarAnamnese(idavaliacao, texto);
	}

	@Transactional
	public String acrescentarComentario(int idavaliacao, String texto) {
		Optional<Avaliacao> avaliacaoOp = Avaliacao.findByIdOptional(idavaliacao);
		Avaliacao avaliacao = avaliacaoOp.orElseThrow(() -> new NotFoundException("AvaliaÃ§Ã£o nÃ£o foi encontrada"));
		String comentarioAtual = avaliacao.getComentario();
		String atual = comentarioAtual == null || comentarioAtual.trim().equals(".") ? "" : comentarioAtual.trim();
		String acrescimo = texto == null ? "" : texto.trim();
		String comentarioFinal = atual.isBlank() ? acrescimo : atual + "\n" + acrescimo;
		avaliacao.setComentario(comentarioFinal);
		return comentarioFinal;
	}

	@Transactional
	public void atualizarCor(int idavaliacao, int idcor, String cor) {
		avaliacaoRepository.atualizarCor(idavaliacao, idcor, cor);
	}

	@Transactional
	public void alterarAvaliado(int idavaliacao, int num) {
		avaliacaoRepository.alterarAvaliado(idavaliacao, num);
	}

	@Transactional
	public void removerEsperaMedico(int idavaliacao, String num) {
		avaliacaoRepository.removerEsperaMedico(idavaliacao, num);
	}

	@Transactional
	// Salvar os setores
	public Avaliacao salvarAvaliacao(AvaliacaoDto dto) {
		Avaliacao avaliacao = new Avaliacao();

		avaliacao.setGlasgow(dto.getGlasgow());
		avaliacao.setNiveldor(dto.getNiveldor());
		avaliacao.setIdmedico(dto.getIdmedico());
		avaliacao.setAnamnese(dto.getAnamnese());
		avaliacao.setPadiast(dto.getPadiast());
		avaliacao.setPasist(dto.getPasist());
		avaliacao.setFc(dto.getFc());
		avaliacao.setTax(dto.getTax());
		avaliacao.setFr(dto.getFr());
		avaliacao.setPeso(dto.getPeso());
		avaliacao.setPeso2(dto.getPeso2());
		avaliacao.setSpo(dto.getSpo());
		avaliacao.setEupneico(dto.isEupneico());
		avaliacao.setDispneico(dto.isDispneico());
		avaliacao.setTaquipneico(dto.isTaquipneico());
		avaliacao.setAcianotico(dto.isAcianotico());
		avaliacao.setHidratado(dto.isHidratado());
		avaliacao.setCianotico(dto.isCianotico());
		avaliacao.setDesidratado(dto.isDesidratado());
		avaliacao.setNormocorado(dto.isNormocorado());
		avaliacao.setHipocorado(dto.isHipocorado());
		avaliacao.setSudoreico(dto.isSudoreico());
		avaliacao.setAnicterico(dto.isAnicterico());
		avaliacao.setIcterico(dto.isIcterico());
		avaliacao.setPulsocheio(dto.isPulsocheio());
		avaliacao.setPulsofino(dto.isPulsofino());
		avaliacao.setEtilismo(dto.isEtilismo());
		avaliacao.setAcordado(dto.isAcordado());
		avaliacao.setOrientado(dto.isOrientado());
		avaliacao.setDesorientado(dto.isDesorientado());
		avaliacao.setTorporoso(dto.isTorporoso());
		avaliacao.setComatoso(dto.isComatoso());
		avaliacao.setIsocorico(dto.isIsocorico());
		avaliacao.setAnisocorico(dto.isAnisocorico());
		avaliacao.setMidriatico(dto.isMidriatico());
		avaliacao.setMiotico(dto.isMiotico());
		avaliacao.setRigideznuca(dto.isRigideznuca());
		avaliacao.setCabeca(dto.getCabeca());
		avaliacao.setAbdome(dto.getAbdome());
		avaliacao.setMembro(dto.getMembro());
		avaliacao.setData(dto.getData());
		avaliacao.setHora(dto.getHora());
		avaliacao.setControle(dto.getControle());
		avaliacao.setAvaliado(dto.getAvaliado());
		avaliacao.setEsperando(dto.getEsperando());
		avaliacao.setIdpaciente(dto.getIdpaci());
		avaliacao.setCor(dto.getCor());
		avaliacao.setComentario(dto.getComentario());
		avaliacao.setHpp(dto.getHpp());
		avaliacao.setRisco(dto.getRisco());
		avaliacao.setAlergiaSouN(dto.isAlergiaSouN());
		avaliacao.setUnidade(dto.getUnidade());

		avaliacao.persist();
		return avaliacao;
	}

	// Alterar
	@Transactional
	public void atualizarAvaliacao(int id, AvaliacaoDto dto) {
		Optional<Avaliacao> avaliacaoOp = Avaliacao.findByIdOptional(id);
		Avaliacao avaliacao = avaliacaoOp.orElseThrow(() -> new NotFoundException("Avaliação não foi encontrada"));

		avaliacao.setGlasgow(dto.getGlasgow());
		avaliacao.setNiveldor(dto.getNiveldor());
		avaliacao.setIdmedico(dto.getIdmedico());
		avaliacao.setAnamnese(dto.getAnamnese());
		avaliacao.setPadiast(dto.getPadiast());
		avaliacao.setPasist(dto.getPasist());
		avaliacao.setFc(dto.getFc());
		avaliacao.setTax(dto.getTax());
		avaliacao.setFr(dto.getFr());
		avaliacao.setPeso(dto.getPeso());
		avaliacao.setPeso2(dto.getPeso2());
		avaliacao.setSpo(dto.getSpo());
		avaliacao.setEupneico(dto.isEupneico());
		avaliacao.setDispneico(dto.isDispneico());
		avaliacao.setTaquipneico(dto.isTaquipneico());
		avaliacao.setAcianotico(dto.isAcianotico());
		avaliacao.setHidratado(dto.isHidratado());
		avaliacao.setCianotico(dto.isCianotico());
		avaliacao.setDesidratado(dto.isDesidratado());
		avaliacao.setNormocorado(dto.isNormocorado());
		avaliacao.setHipocorado(dto.isHipocorado());
		avaliacao.setSudoreico(dto.isSudoreico());
		avaliacao.setAnicterico(dto.isAnicterico());
		avaliacao.setIcterico(dto.isIcterico());
		avaliacao.setPulsocheio(dto.isPulsocheio());
		avaliacao.setPulsofino(dto.isPulsofino());
		avaliacao.setEtilismo(dto.isEtilismo());
		avaliacao.setAcordado(dto.isAcordado());
		avaliacao.setOrientado(dto.isOrientado());
		avaliacao.setDesorientado(dto.isDesorientado());
		avaliacao.setTorporoso(dto.isTorporoso());
		avaliacao.setComatoso(dto.isComatoso());
		avaliacao.setIsocorico(dto.isIsocorico());
		avaliacao.setAnisocorico(dto.isAnisocorico());
		avaliacao.setMidriatico(dto.isMidriatico());
		avaliacao.setMiotico(dto.isMiotico());
		avaliacao.setRigideznuca(dto.isRigideznuca());
		avaliacao.setCabeca(dto.getCabeca());
		avaliacao.setAbdome(dto.getAbdome());
		avaliacao.setMembro(dto.getMembro());
		avaliacao.setData(dto.getData());
		avaliacao.setHora(dto.getHora());
		// avaliacao.setControle(dto.getControle());
		avaliacao.setAvaliado(dto.getAvaliado());
		avaliacao.setEsperando(dto.getEsperando());
		avaliacao.setIdpaciente(dto.getIdpaci());
		avaliacao.setCor(dto.getCor());
		avaliacao.setComentario(dto.getComentario());
		avaliacao.setHpp(dto.getHpp());
		avaliacao.setRisco(dto.getRisco());
		avaliacao.setAlergiaSouN(dto.isAlergiaSouN());
		avaliacao.setUnidade(dto.getUnidade());

		avaliacao.persist();
	}

	public long contagemDeAtendimentos(String unidade, String data) {
		return avaliacaoRepository.contagemDeAtendimentos(unidade, data);
	}
	
	public long contagemDeAtendimentosHoje(String unidade, String data) {
		return avaliacaoRepository.contagemDeAtendimentosHoje(unidade, data);
	}

	public List<Map<String, Object>> contagemDeAtendimentosHojePorSetor(String unidade, String data) {
		return avaliacaoRepository.contagemDeAtendimentosHojePorSetor(unidade, data);
	}

	public List<Map<String, Object>> contagemDeAtendimentosSemanaPorSetor(String unidade, String data) {
		return avaliacaoRepository.contagemDeAtendimentosSemanaPorSetor(unidade, data);
	}

	public List<Map<String, Object>> contagemDeAtendimentosMesPorSetor(String unidade, String data) {
		return avaliacaoRepository.contagemDeAtendimentosMesPorSetor(unidade, data);
	}

	public List<Map<String, Object>> contagemDeAtendimentosAnoPorSetor(String unidade, String data) {
		return avaliacaoRepository.contagemDeAtendimentosAnoPorSetor(unidade, data);
	}
	
	

}
