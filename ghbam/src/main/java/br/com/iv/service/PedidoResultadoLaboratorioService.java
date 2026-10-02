package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

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

	public List<PedidoResultadoLaboratorio> filtrarBamPedidoResultadoLaboratorio(int idbam) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio2Op = pedidoResultadoLaboratorioRepository
				.filtrarBamPedidoResultadoLaboratorio(idbam);
		if (pedidoResultadoLaboratorio2Op.isEmpty()) {
			System.out.println("Resultados de exames não encontrado...");
		}
		return pedidoResultadoLaboratorio2Op;
	}

	public List<PedidoResultadoLaboratorio> filtrarPeloIDPaciente(int idpaciente) {
		List<PedidoResultadoLaboratorio> pedidoResultadoLaboratorio2Op = pedidoResultadoLaboratorioRepository
				.filtrarPeloIDPaciente(idpaciente);
		if (pedidoResultadoLaboratorio2Op.isEmpty()) {
			System.out.println("Resultados de exames não encontrado...");
		}
		return pedidoResultadoLaboratorio2Op;
	}

	@Transactional
	public void pdfExame(int id, String arquivo, String liberou) {
		pedidoResultadoLaboratorioRepository.pdfExame(id, arquivo, liberou);
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
		// Só vai para a fila RNDS quem foi preparado para ela (painel RNDS ou exames lidos do PDF).
		if (temItens || temDadosRnds(pedidoResultadoLaboratorio)) {
			fhirDispatchService.enfileirarResultadoLaboratorial(pedidoResultadoLaboratorio);
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

	private static boolean temDadosRnds(PedidoResultadoLaboratorio resultado) {
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
		return pedidoResultadoLaboratorio2Op;
	}

	// Texto vazio vira null; maximo 0 = sem limite (colunas TEXT).
	static String limitar(String valor, int maximo) {
		if (valor == null || valor.isBlank()) return null;
		String texto = valor.trim();
		return maximo > 0 && texto.length() > maximo ? texto.substring(0, maximo) : texto;
	}

}
