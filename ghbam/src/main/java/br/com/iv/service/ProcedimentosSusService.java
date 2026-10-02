package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.ProcedimentoSusValorEfetivoDto;
import br.com.iv.dto.ProcedimentoSusValorUnidadeDto;
import br.com.iv.entity.ProcedimentoSusValorUnidade;
import br.com.iv.entity.ProcedimentosRX;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.repository.ProcedimentoSusValorUnidadeRepository;
import br.com.iv.repository.ProcedimentosRXRepository;
import br.com.iv.repository.ProcedimentosSusRepository;

@ApplicationScoped
public class ProcedimentosSusService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ProcedimentosSusRepository procedimentosSusRepository;

	@Inject
	ProcedimentoSusValorUnidadeRepository procedimentoSusValorUnidadeRepository;

	@Inject
	ProcedimentosRXRepository procedimentosRXRepository;

	public Object ProcedimentoSusId(String procedimento) {
        // 1) tenta buscar no SUS
        ProcedimentosSus sus = procedimentosSusRepository.filtrarPorUnicoProcedimentoSus(procedimento);
        if (sus != null) {
            return sus;
        }

        // 2) tenta buscar no RX
        Object rx = procedimentosSusRepository.buscarNoRxProcedimentos(procedimento);
        if (rx != null) {
            return rx;
        }

        // 3) não achou em nenhum
        return null;
    }


	public List<ProcedimentosSus> procediment(String texto, String cid, String cbo) {
		List<ProcedimentosSus> procedimentosSusOP = procedimentosSusRepository.procediment(texto, cid, cbo);
		if (procedimentosSusOP.isEmpty()) {
			System.out.println("Nenhum procedimento foi encontrado...");
		}
		return procedimentosSusOP;
	}
	
	public List<ProcedimentosSus> procediment2(String texto) {
		List<ProcedimentosSus> procedimentosSusOP = procedimentosSusRepository.procediment2(texto);
		if (procedimentosSusOP.isEmpty()) {
			System.out.println("Nenhum procedimento foi encontrado...");
		}
		return procedimentosSusOP;
	}

	public List<ProcedimentoSusValorEfetivoDto> pesquisarValores(String termo, String unidade, int limite) {
		List<ProcedimentosSus> procedimentos = procedimentosSusRepository.pesquisarValores(termo, limite);
		Map<String, ProcedimentosRX> rxPorCodigo = buscarRxPorCodigo(procedimentos);

		return procedimentos
				.stream()
				.map(procedimento -> montarValorEfetivo(
						procedimento,
						unidade,
						rxPorCodigo.get(codigoCompleto(procedimento))))
				.collect(Collectors.toList());
	}

	public ProcedimentoSusValorEfetivoDto buscarValorEfetivo(String procedimento, String unidade) {
		ProcedimentosSus sus = procedimentosSusRepository.filtrarPorUnicoProcedimentoSus(procedimento);
		if (sus == null) {
			return null;
		}
		ProcedimentosRX rx = procedimentosRXRepository.filtrarPorUnicoProcedimentoRX(procedimento, sus.getPa_dc());
		if (rx == null) {
			rx = procedimentosRXRepository.listarPorCodigos(List.of(procedimento)).stream().findFirst().orElse(null);
		}
		return montarValorEfetivo(sus, unidade, rx);
	}

	@Transactional
	public ProcedimentoSusValorEfetivoDto salvarValorUnidade(ProcedimentoSusValorUnidadeDto dto) {
		if (dto == null || vazio(dto.getUnidade()) || vazio(dto.getPa_id()) || vazio(dto.getPa_dv())) {
			throw new IllegalArgumentException("Unidade, pa_id e pa_dv sao obrigatorios.");
		}

		ProcedimentosSus sus = procedimentosSusRepository.filtrarPorPaIdDv(dto.getPa_id(), dto.getPa_dv());
		if (sus == null) {
			throw new IllegalArgumentException("Procedimento SUS nao encontrado.");
		}

		ProcedimentoSusValorUnidade valor = procedimentoSusValorUnidadeRepository.buscar(
				dto.getUnidade(),
				dto.getPa_id(),
				dto.getPa_dv());

		if (valor == null) {
			valor = new ProcedimentoSusValorUnidade();
			valor.setUnidade(dto.getUnidade());
			valor.setPa_id(dto.getPa_id());
			valor.setPa_dv(dto.getPa_dv());
		}

		valor.setPa_sa(dto.getPa_sa());
		valor.setPa_sp(dto.getPa_sp());
		valor.setPa_sh(dto.getPa_sh());
		valor.setUsarValorPersonalizado(dto.isUsarValorPersonalizado());
		valor.setAtualizadoPor(dto.getAtualizadoPor());
		valor.setAtualizadoEm(LocalDateTime.now());
		procedimentoSusValorUnidadeRepository.persist(valor);

		ProcedimentosRX rx = procedimentosRXRepository.listarPorCodigos(List.of(dto.getPa_id() + dto.getPa_dv()))
				.stream()
				.findFirst()
				.orElse(null);

		return montarValorEfetivo(sus, dto.getUnidade(), rx);
	}

	private ProcedimentoSusValorEfetivoDto montarValorEfetivo(ProcedimentosSus sus, String unidade) {
		return montarValorEfetivo(sus, unidade, null);
	}

	private ProcedimentoSusValorEfetivoDto montarValorEfetivo(ProcedimentosSus sus, String unidade, ProcedimentosRX rx) {
		ProcedimentoSusValorUnidade valorUnidade = null;
		if (!vazio(unidade)) {
			valorUnidade = procedimentoSusValorUnidadeRepository.buscar(unidade, sus.getPa_id(), sus.getPa_dv());
		}

		boolean usarPersonalizado = valorUnidade != null && valorUnidade.isUsarValorPersonalizado();

		ProcedimentoSusValorEfetivoDto dto = new ProcedimentoSusValorEfetivoDto();
		dto.setIdprocedimentossus(sus.getIdprocedimentossus());
		dto.setIdprocedimentovalorunidade(valorUnidade == null ? null : valorUnidade.getIdprocedimentovalorunidade());
		dto.setUnidade(unidade);
		dto.setPa_id(sus.getPa_id());
		dto.setPa_dv(sus.getPa_dv());
		dto.setPa_dc(sus.getPa_dc());
		dto.setPa_sa_padrao(sus.getPa_sa());
		dto.setPa_sp_padrao(sus.getPa_sp());
		dto.setPa_sh_padrao(sus.getPa_sh());
		dto.setPa_sa(usarPersonalizado ? valorUnidade.getPa_sa() : sus.getPa_sa());
		dto.setPa_sp(usarPersonalizado ? valorUnidade.getPa_sp() : sus.getPa_sp());
		dto.setPa_sh(usarPersonalizado ? valorUnidade.getPa_sh() : sus.getPa_sh());
		dto.setPa_eletiva(sus.getPa_eletiva());
		dto.setPa_tpreg(sus.getPa_tpreg());
		dto.setPersonalizado(valorUnidade != null);
		dto.setUsarValorPersonalizado(usarPersonalizado);
		dto.setUsadoRx(rx != null);
		if (rx != null) {
			dto.setDescricaoRx(rx.getDescricao());
			dto.setModoRx(rx.getModo());
			dto.setModo2Rx(rx.getModo2());
		}
		return dto;
	}

	private Map<String, ProcedimentosRX> buscarRxPorCodigo(List<ProcedimentosSus> procedimentos) {
		List<String> codigos = procedimentos.stream()
				.map(this::codigoCompleto)
				.distinct()
				.collect(Collectors.toList());

		return procedimentosRXRepository.listarPorCodigos(codigos)
				.stream()
				.collect(Collectors.toMap(
						ProcedimentosRX::getCodprocedimento,
						Function.identity(),
						(primeiro, ignorado) -> primeiro));
	}

	private String codigoCompleto(ProcedimentosSus procedimento) {
		return procedimento.getPa_id() + procedimento.getPa_dv();
	}

	private boolean vazio(String valor) {
		return valor == null || valor.trim().isEmpty();
	}


	/**
	 * CIDs aceitos pelo SIGTAP para o procedimento informado.
	 *
	 * O codigo chega da tela com o digito verificador colado (pa_id + pa_dv) e
	 * s_pacid pode ter sido carregada com ou sem esse digito, entao as duas formas
	 * sao consultadas.
	 */
	public List<Map<String, String>> cidsDoProcedimento(String codigo) {
		String limpo = codigo == null ? "" : codigo.replaceAll("[^0-9]", "");
		String semDv = limpo.length() == 10 ? limpo.substring(0, 9) : limpo;
		List<Object[]> linhas = procedimentosSusRepository.cidsDoProcedimento(limpo, semDv);
		List<Map<String, String>> cids = new ArrayList<>();
		for (Object[] linha : linhas) {
			cids.add(Map.of(
					"codigo", linha[0] == null ? "" : String.valueOf(linha[0]).trim(),
					"descricao", linha[1] == null ? "" : String.valueOf(linha[1]).trim()));
		}
		return cids;
	}
}
