package br.com.iv.service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.panache.Panache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.PedidoResultadoLaboratorioDto;
import br.com.iv.dto.PedidoResultadoLaboratorioItemDto;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.entity.PedidoResultadoLaboratorioItem;
import br.com.iv.repository.PedidoResultadoLaboratorioRepository;

@ApplicationScoped
public class PedidoResultadoLaboratorioService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	PedidoResultadoLaboratorioRepository pedidoResultadoLaboratorioRepository;

	@Inject
	FhirDispatchService fhirDispatchService;

	@Inject
	ArmazenamentoArquivoService armazenamento;

	@Inject
	ResultadoInfecciosoLaudoService resultadoInfecciosoLaudoService;

	private static final Logger LOG = Logger.getLogger(PedidoResultadoLaboratorioService.class);
	// Abaixo disso nao e laudo em PDF (as telas tratam texto curto como "sem PDF").
	private static final int TAMANHO_MINIMO_ARMAZENAR = 600;

	public List<PedidoResultadoLaboratorio> filtrarBamPedidoResultadoLaboratorio(int idbam) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio2Op = pedidoResultadoLaboratorioRepository
				.filtrarBamPedidoResultadoLaboratorio(idbam);
		if (pedidoResultadoLaboratorio2Op.isEmpty()) {
			System.out.println("Resultados de exames não encontrado...");
		}
		return preencherArquivos(pedidoResultadoLaboratorio2Op);
	}

	/**
	 * Guarda o laudo no MinIO exatamente como chegou (o texto Base64/data URL),
	 * para as telas receberem de volta o mesmo valor. Devolve null quando fica no banco.
	 */
	private String guardarArquivo(int idPedido, String unidade, int bam, String arquivo) {
		if (!armazenamento.habilitado() || arquivo == null || arquivo.length() < TAMANHO_MINIMO_ARMAZENAR) return null;
		try {
			return armazenamento.salvar("laboratorio/" + ArmazenamentoArquivoService.pastaUnidade(unidade) + "/" + pastaPacienteDoBam(bam)
					+ "pedido-" + idPedido + "-" + UUID.randomUUID(),
					arquivo.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
		} catch (RuntimeException falha) {
			LOG.warnf("Laboratorio: MinIO indisponivel, laudo do pedido %d gravado no banco: %s", idPedido, falha.getMessage());
			return null;
		}
	}

	/** Nome e id do paciente pelo BAM do pedido; sem BAM valido, fica so a pasta da unidade. */
	private String pastaPacienteDoBam(int bam) {
		return armazenamento.pastaPacienteDoBam(bam);
	}

	/** Recoloca no campo arquivo o laudo que esta no MinIO, sem regravar no banco. */
	private List<PedidoResultadoLaboratorio> preencherArquivos(List<PedidoResultadoLaboratorio> resultados) {
		for (PedidoResultadoLaboratorio r : resultados) {
			String chave = r.getArmazenamentoChave();
			if (chave == null || chave.isBlank()) continue;
			Panache.getEntityManager().detach(r);
			try {
				r.setArquivo(new String(armazenamento.ler(chave), StandardCharsets.UTF_8));
			} catch (RuntimeException falha) {
				LOG.errorf("Laboratorio: falha ao ler laudo do pedido %d do MinIO: %s", r.getIdPedidoResultado(), falha.getMessage());
			}
		}
		return resultados;
	}

	public List<PedidoResultadoLaboratorio> filtrarPeloIDPaciente(int idpaciente) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio2Op = pedidoResultadoLaboratorioRepository
				.filtrarPeloIDPaciente(idpaciente);
		if (pedidoResultadoLaboratorio2Op.isEmpty()) {
			System.out.println("Resultados de exames não encontrado...");
		}
		return preencherArquivos(pedidoResultadoLaboratorio2Op);
	}

	@Transactional
	public void pdfExame(int id, String arquivo, String liberou) {
		PedidoResultadoLaboratorio pedido = PedidoResultadoLaboratorio.findById(id);
		String chave = guardarArquivo(id, pedido == null ? null : pedido.getUnidade(), pedido == null ? 0 : pedido.getBam(), arquivo);
		if (chave != null) pedidoResultadoLaboratorioRepository.pdfExameArmazenado(id, chave);
		else pedidoResultadoLaboratorioRepository.pdfExame(id, arquivo, liberou);
	}

	// Salvar
	@Transactional
	public PedidoResultadoLaboratorio salvarPedidoResultadoLaboratorio(PedidoResultadoLaboratorioDto dto) {
		PedidoResultadoLaboratorio pedidoResultadoLaboratorio = new PedidoResultadoLaboratorio();

		pedidoResultadoLaboratorio.setMedico(dto.getMedico());
		pedidoResultadoLaboratorio.setCrm(dto.getCrm());
		pedidoResultadoLaboratorio.setSetor(dto.getSetor());
		pedidoResultadoLaboratorio.setLocal(dto.getLocal());
		pedidoResultadoLaboratorio.setBam(dto.getBam());
		pedidoResultadoLaboratorio.setArquivo(dto.getArquivo());
		pedidoResultadoLaboratorio.setUnidade(dto.getUnidade());
		pedidoResultadoLaboratorio.setDescricao(dto.getDescricao());
		pedidoResultadoLaboratorio.setGrupopedidos(dto.getGrupopedidos());
		copiarDadosRnds(dto, pedidoResultadoLaboratorio);
		pedidoResultadoLaboratorio.persist();
		boolean temItens = substituirItens(pedidoResultadoLaboratorio.getIdPedidoResultado(), dto.getItens());
		try {
			resultadoInfecciosoLaudoService.gravar(pedidoResultadoLaboratorio.getIdPedidoResultado(), dto.getBam(), dto.getResultadosInfecciosos());
		} catch (RuntimeException falha) {
			// O laudo vale mais que a leitura para a vigilancia: registra sem dado do exame e segue.
			LOG.errorf("Laboratorio: falha ao gravar resultado infeccioso do pedido %d: %s", pedidoResultadoLaboratorio.getIdPedidoResultado(), falha.getClass().getSimpleName());
		}
		// Só vai para a fila RNDS quem foi preparado para ela (painel RNDS ou exames lidos do PDF).
		if (temItens || temDadosRnds(pedidoResultadoLaboratorio)) {
			fhirDispatchService.enfileirarResultadoLaboratorial(pedidoResultadoLaboratorio);
		}
		String arquivo = pedidoResultadoLaboratorio.getArquivo();
		String chave = guardarArquivo(pedidoResultadoLaboratorio.getIdPedidoResultado(), pedidoResultadoLaboratorio.getUnidade(),
				pedidoResultadoLaboratorio.getBam(), arquivo);
		if (chave != null) {
			pedidoResultadoLaboratorio.setArquivo("");
			pedidoResultadoLaboratorio.setArmazenamentoChave(chave);
			Panache.getEntityManager().flush();
			// A resposta devolve o laudo como antes; o banco ja ficou so com a chave.
			Panache.getEntityManager().detach(pedidoResultadoLaboratorio);
			pedidoResultadoLaboratorio.setArquivo(arquivo);
		}
		return pedidoResultadoLaboratorio;
	}

	// Resultados salvos antes dos campos RNDS: completa os dados e (re)coloca na fila.
	@Transactional
	public PedidoResultadoLaboratorio completarDadosRnds(int idPedido, PedidoResultadoLaboratorioDto dto) {
		PedidoResultadoLaboratorio resultado = PedidoResultadoLaboratorio.findById(idPedido);
		if (resultado == null) return null;
		copiarDadosRnds(dto, resultado);
		boolean temItens = dto.getItens() == null
				? PedidoResultadoLaboratorioItem.count("idpedidoresultado", idPedido) > 0
				: substituirItens(idPedido, dto.getItens());
		if (temItens || temDadosRnds(resultado)) {
			fhirDispatchService.enfileirarResultadoLaboratorial(resultado);
		}
		return resultado;
	}

	// Troca os exames do laudo pelos enviados; itens == null mantém os atuais.
	// Retorna se o resultado ficou com algum item.
	private boolean substituirItens(int idPedido, List<PedidoResultadoLaboratorioItemDto> itens) {
		if (itens == null) return false;
		PedidoResultadoLaboratorioItem.delete("idpedidoresultado", idPedido);
		int ordem = 0;
		for (PedidoResultadoLaboratorioItemDto dto : itens) {
			String nome = limitar(dto.getNomeExame(), 120);
			String valor = limitar(dto.getValor(), 60);
			if (nome == null || valor == null) continue;
			PedidoResultadoLaboratorioItem item = new PedidoResultadoLaboratorioItem();
			item.setIdpedidoresultado(idPedido);
			item.setNomeExame(nome);
			item.setValor(valor);
			item.setCodigoLoinc(limitar(dto.getCodigoLoinc(), 30));
			item.setUnidadeMedida(limitar(dto.getUnidadeMedida(), 50));
			item.setReferenciaValor(limitar(dto.getReferenciaValor(), 120));
			item.setOrdem(ordem++);
			item.persist();
		}
		return ordem > 0;
	}

	// Campos estruturados para RNDS (V188); cortados no tamanho da coluna para
	// uma sugestão longa extraída do PDF não impedir o salvamento do resultado.
	private void copiarDadosRnds(PedidoResultadoLaboratorioDto dto, PedidoResultadoLaboratorio destino) {
		destino.setValorEstruturado(limitar(dto.getValorEstruturado(), 0));
		destino.setUnidadeMedida(limitar(dto.getUnidadeMedida(), 50));
		destino.setReferenciaValor(limitar(dto.getReferenciaValor(), 120));
		destino.setAmostraBiologica(limitar(dto.getAmostraBiologica(), 120));
		destino.setDataColeta(limitar(dto.getDataColeta(), 10));
		destino.setCodigoLoinc(limitar(dto.getCodigoLoinc(), 30));
		destino.setJustificativaAusenciaValor(limitar(dto.getJustificativaAusenciaValor(), 0));
	}

	static boolean temDadosRnds(PedidoResultadoLaboratorio resultado) {
		return resultado.getCodigoLoinc() != null
				|| resultado.getValorEstruturado() != null
				|| resultado.getJustificativaAusenciaValor() != null;
	}

	public List<PedidoResultadoLaboratorio> lista_de_resultados_de_exames_pedidomedico2_lab_result(int idbam) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio2Op = pedidoResultadoLaboratorioRepository
				.lista_de_resultados_de_exames_pedidomedico2_lab_result(idbam);
		if (pedidoResultadoLaboratorio2Op.isEmpty()) {
			System.err.println("Nenhum resultados realizado de exames não encontrado...");
		}
		return preencherArquivos(pedidoResultadoLaboratorio2Op);
	}

	// Texto vazio vira null; maximo 0 = sem limite (colunas TEXT).
	static String limitar(String valor, int maximo) {
		if (valor == null || valor.isBlank()) return null;
		String texto = valor.trim();
		return maximo > 0 && texto.length() > maximo ? texto.substring(0, maximo) : texto;
	}

}
