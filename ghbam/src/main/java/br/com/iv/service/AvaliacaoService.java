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

	// Contadores de painel por setor: to_date(a.data) nao usa indice (~1,4 s por chamada) e o
	// painel repete a consulta a cada poucos segundos; segundos de atraso nao mudam a leitura.
	private static final long SETOR_CACHE_CURTO_MS = 60 * 1000L;
	private static final long SETOR_CACHE_LONGO_MS = 5 * 60 * 1000L;
	private static final Map<String, Object[]> SETOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> porSetorEmCache(String periodo, String unidade, String data, long validadeMs,
			java.util.function.Supplier<List<Map<String, Object>>> consulta) {
		String chave = periodo + "|" + unidade + "|" + data;
		Object[] cache = SETOR_CACHE.get(chave);
		long agora = System.currentTimeMillis();
		if (cache != null && agora - (Long) cache[0] < validadeMs) return (List<Map<String, Object>>) cache[1];
		List<Map<String, Object>> resultado = consulta.get();
		if (SETOR_CACHE.size() > 2000) SETOR_CACHE.clear();
		SETOR_CACHE.put(chave, new Object[] { agora, resultado });
		return resultado;
	}

	// O Dashboard pede este contador para cada dia do mes (~30 chamadas por abertura);
	// dia ja encerrado praticamente nao muda e fica 1 h em cache.
	private static final long SETOR_CACHE_DIA_ENCERRADO_MS = 60 * 60 * 1000L;

	private static long validadePorDia(String data) {
		try {
			return java.time.LocalDate.parse(data.replace('/', '-')).isBefore(java.time.LocalDate.now())
					? SETOR_CACHE_DIA_ENCERRADO_MS : SETOR_CACHE_CURTO_MS;
		} catch (RuntimeException e) {
			return SETOR_CACHE_CURTO_MS;
		}
	}

	public List<Map<String, Object>> contagemDeAtendimentosHojePorSetor(String unidade, String data) {
		return porSetorEmCache("hoje", unidade, data, validadePorDia(data),
				() -> avaliacaoRepository.contagemDeAtendimentosHojePorSetor(unidade, data));
	}

	public List<Map<String, Object>> contagemDeAtendimentosSemanaPorSetor(String unidade, String data) {
		return porSetorEmCache("semana", unidade, data, SETOR_CACHE_CURTO_MS,
				() -> avaliacaoRepository.contagemDeAtendimentosSemanaPorSetor(unidade, data));
	}

	public List<Map<String, Object>> contagemDeAtendimentosMesPorSetor(String unidade, String data) {
		return porSetorEmCache("mes", unidade, data, SETOR_CACHE_LONGO_MS,
				() -> avaliacaoRepository.contagemDeAtendimentosMesPorSetor(unidade, data));
	}

	public List<Map<String, Object>> contagemDeAtendimentosAnoPorSetor(String unidade, String data) {
		return porSetorEmCache("ano", unidade, data, SETOR_CACHE_LONGO_MS,
				() -> avaliacaoRepository.contagemDeAtendimentosAnoPorSetor(unidade, data));
	}
	
	

}
