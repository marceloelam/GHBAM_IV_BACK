package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import br.com.iv.dto.NovoInternadoDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.NovoInternado;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.NovoInternadoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NovoInternadoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	NovoInternadoRepository novointernadoRepository;

	@Inject
	BamRepository bamRepository;

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
	public NovoInternado alterarLeito(int bam, int setor, int leito, String quem) {
		novointernadoRepository.alterarLeito(bam, setor, leito, quem);
		return null;
	}

	@SuppressWarnings("static-access")
	@Transactional
	public NovoInternado alterarAltaDoPaciente(int idbam, LocalDate acao, boolean alta, String quem, String motivo,
			int idnovo) {
		novointernadoRepository.alterarAltaDoPaciente(idbam, acao, alta, quem, motivo, idnovo);
		return null;
	}

	@SuppressWarnings("static-access")
	@Transactional
	public NovoInternado deletarAlocacao(int idbam) {
		novointernadoRepository.deletarAlocacao(idbam);
		return null;
	}

	// Salvar
	@Transactional
	public NovoInternado salvarNovoInternado(NovoInternadoDto dto) {
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

			novoInternado.persist();
			bamRepository.atualizarMarcadorTransferencia(numeroDoBam.getIdbam(), false);
			return novoInternado;

		} catch (Exception e) {

		}
		return null;

	}

	public List<NovoInternado> listarBamUnidade(String bam, String unidade) {
		return novointernadoRepository.listarBamUnidade(bam,unidade);
		}

	@Transactional
	public boolean cancelarAltaNoCenso(int bam) {
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
