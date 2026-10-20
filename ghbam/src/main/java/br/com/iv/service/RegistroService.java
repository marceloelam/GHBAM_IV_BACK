package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.RegistroDto;
import br.com.iv.entity.Registro;

import br.com.iv.repository.RegistroRepository;

@ApplicationScoped
public class RegistroService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	RegistroRepository registroRepository;

	// Listando todos os setores
	public List<Registro> listarTodosOsRegistros() {
		return registroRepository.listAll();
	}

	public Registro listarPorID(int id) {
		return registroRepository.filtrarPorId(id);
	}

	public Registro listarPorControle(String controle) {
		return registroRepository.filtrarPorControle(controle);
	}

	public long contarAtendimentosNaSemana(String servidor, String unidade, String quando) {
		return registroRepository.contarAtendimentosNaSemana(servidor, unidade, quando);
	}

	
	public List<Registro> duploAtendimentoMedico(String unidade, String medico) {
		List<Registro> registroOP = registroRepository.duploAtendimentoMedico(unidade, medico);
		if (registroOP.isEmpty()) {
			System.out.println("Não há registro de atendimento duplo....");
		}
		return registroOP;
	}

	@Transactional
	// Salvar os setores
	public Registro salvarRegistro(RegistroDto dto) {
		Registro registro = new Registro();

		registro.setResponsavel(dto.getResponsavel());
		registro.setSetor(dto.getSetor());
		registro.setEspecialidade(dto.getEspecialidade());
		registro.setConvenio(dto.getConvenio());
		registro.setTipochegada(dto.getTipochegada());
		registro.setTipoocorrencia(dto.getTipoocorrencia());
		registro.setQueixaprincipal(dto.getQueixaprincipal());
		registro.setCausalesao(dto.getCausalesao());
		registro.setObsentrada(dto.getObsentrada());
		registro.setResultadoexame(dto.isResultadoexame());
		registro.setResultadorx(dto.isResultadorx());
		registro.setResultadotomo(dto.isResultadotomo());
		registro.setResultadolabo(dto.isResultadolabo());
		registro.setResultadomed(dto.isResultadomed());
		registro.setControle(dto.getControle());
		registro.setAtendidopor(dto.getAtendidopor());
		registro.setMatriculaatendidopor(dto.getMatriculaatendidopor());
		registro.setResultadoexamec19(dto.getResultadoexamec19());
		registro.setEletivo(dto.isEletivo());
		registro.setEletivointernado(dto.isEletivointernado());
		registro.setDtsintomas(dto.getDtsintomas());

		registro.persist();
		return registro;
	}

	// Alterar
	@Transactional
	public void atualizarRegistro(int id, RegistroDto dto) {
		Optional<Registro> registroOp = Registro.findByIdOptional(id);
		Registro registro = registroOp.orElseThrow(() -> new NullPointerException("Registro não foi encontrado..."));

		registro.setResponsavel(dto.getResponsavel());
		registro.setSetor(dto.getSetor());
		registro.setEspecialidade(dto.getEspecialidade());
		registro.setConvenio(dto.getConvenio());
		registro.setTipochegada(dto.getTipochegada());
		registro.setTipoocorrencia(dto.getTipoocorrencia());
		registro.setQueixaprincipal(dto.getQueixaprincipal());
		registro.setCausalesao(dto.getCausalesao());
		registro.setObsentrada(dto.getObsentrada());
		registro.setResultadoexame(dto.isResultadoexame());
		registro.setResultadorx(dto.isResultadorx());
		registro.setResultadotomo(dto.isResultadotomo());
		registro.setResultadomed(dto.isResultadomed());
		registro.setResultadolabo(dto.isResultadolabo());
		// registro.setControle(dto.getControle());
		registro.setAtendidopor(dto.getAtendidopor());
		registro.setMatriculaatendidopor(dto.getMatriculaatendidopor());
		registro.setResultadoexamec19(dto.getResultadoexamec19());
		registro.setEletivo(dto.isEletivo());
		registro.setEletivointernado(dto.isEletivointernado());
		registro.setDtsintomas(dto.getDtsintomas());
		registro.persist();
	}

	@Transactional
	public void atualizarNovoSetorDeAtendimento(int idregistro, String novoSetor) {
		registroRepository.atualizarNovoSetorDeAtendimento(idregistro, novoSetor);
	}

	@Transactional
	public void atualizarRegistroEspera(int idregistro, String atendidopor) {
		registroRepository.atualizarRegistroEspera(idregistro, atendidopor);
	}

	@Transactional
	public int atualizarRegistroResponsavelObservacao(int idRegistro, String responsavel, String obs) {
		return registroRepository.atualizarRegistroResponsavelObservacao(idRegistro, responsavel, obs);
	}
	
	@Transactional
	public void liberarPaciente(int idRegistro) {
		registroRepository.liberarPaciente(idRegistro);
	}
	
	
	@Transactional
	public void atualizarRegistroAtendidoPorNaInternacao(int idRegistro) {
		registroRepository.atualizarRegistroAtendidoPorNaInternacao(idRegistro);
	}

	@Transactional
	public void atualizarRegistroinformarexame(int bam, String local) {
		registroRepository.atualizarRegistroinformarexame(bam, local);

		
	}
}
