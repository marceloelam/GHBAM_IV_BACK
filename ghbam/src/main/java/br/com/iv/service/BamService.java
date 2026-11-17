package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.BamDto;
import br.com.iv.entity.Bam;
import br.com.iv.repository.BamRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BamService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	BamRepository bamRepository;

	public Bam filtarPorUnicoBam(int id) {
		return bamRepository.filtrarPorUnicoBam(id);
	}

	public Bam filtarPorUnicoRegistro(int id) {
		return bamRepository.filtrarPorUnicoRegistro(id);
	}

	public Bam listarPorControle(String controle) {
		return bamRepository.filtrarPorUnicoBamControle(controle);
	}

	public Bam filtrarPorIdDoPcienteUltimoBam(int idpac, String unidade) {
		return bamRepository.filtrarPorIdDoPcienteUltimoBam(idpac, unidade);
	}

	public List<Bam> filtrarPelaUnidadeDoBam(String unidade, LocalDate data1, LocalDate data2) {
		List<Bam> bamOp = bamRepository.filtrarBamPelaUnidade(unidade, data1, data2);
		if (bamOp.isEmpty()) {
			System.out.println("Bam não encontrado...");
		}
		return bamOp;
	}

	public List<Bam> filtarBamPorUnidade(int numero, String unidade) {
		List<Bam> bamOp = bamRepository.filtrarBamEUnidade(numero, unidade);
		if (bamOp.isEmpty()) {
			System.out.println("Bam não encontrado...");
		}
		return bamOp;
	}

	public List<Bam> filtarBamPorIdPaciente(int idpaciente) {
		List<Bam> bamOp = bamRepository.filtrarPorIdDoPciente(idpaciente);
		if (bamOp.isEmpty()) {
			System.out.println("Bam não encontrado...");
		}
		return bamOp;
	}

	@Transactional
	public void atualizarCorNoBam(int idbam, int cor, int idServidor) {
		bamRepository.atualizarCorNoBam(idbam, cor, idServidor);
	}

	@Transactional
	public void atualizarEmAtendimento(int idbam, boolean ematendimento) {
		bamRepository.atualizarEmAtendimento(idbam, ematendimento);
	}

	@Transactional
	public void atualizarOrigemDestinoTransferencia(int idbam, String origem, String destino) {
		bamRepository.atualizarOrigemDestinoTransferencia(idbam, origem, destino);
	}
	
	
	
	/** null = ainda nao conferido; false = banco sem a V232 (trava desligada). */
	private static volatile Boolean colunaUnificadoEmExiste;

	/**
	 * Cadastro ja unificado em outro (V232) nao recebe BAM novo: o atendimento
	 * ficaria de novo separado do historico. Se o banco nao tem a coluna, nao trava
	 * (consultar coluna inexistente abortaria a transacao e pararia a recepcao).
	 */
	private void recusarCadastroUnificado(int idpaciente) {
		var em = br.com.iv.entity.Pacientes.getEntityManager();
		if (colunaUnificadoEmExiste == null) {
			Number n = (Number) em.createNativeQuery("SELECT count(*) FROM information_schema.columns "
					+ "WHERE table_name = 'pacientes' AND column_name = 'unificado_em'").getSingleResult();
			colunaUnificadoEmExiste = n != null && n.longValue() > 0;
		}
		if (!colunaUnificadoEmExiste) {
			return;
		}
		@SuppressWarnings("unchecked")
		java.util.List<Object> destino = em.createNativeQuery(
				"SELECT unificado_em FROM pacientes WHERE idpaciente = :id AND unificado_em IS NOT NULL")
				.setParameter("id", idpaciente).getResultList();
		if (!destino.isEmpty()) {
			throw new jakarta.ws.rs.WebApplicationException(
					"Este cadastro foi unificado no paciente " + destino.get(0)
							+ ". Abra o atendimento no cadastro que ficou.",
					jakarta.ws.rs.core.Response.Status.CONFLICT);
		}
	}

	// Salvar bam
	@Transactional
	public Bam salvarBam(BamDto dto) {
		Bam bam = new Bam();
		// Anexar entidades relacionadas como gerenciadas para evitar TransientPropertyValueException
		if (dto.getRegistro() != null) {
			if (dto.getRegistro().getIdregistro() > 0) {
				bam.setRegistro(br.com.iv.entity.Registro.findById(dto.getRegistro().getIdregistro()));
			} else {
				dto.getRegistro().persist();
				bam.setRegistro(dto.getRegistro());
			}
		}
		if (dto.getAvaliacao() != null) {
			if (dto.getAvaliacao().getIdavaliacao() > 0) {
				bam.setAvaliacao(br.com.iv.entity.Avaliacao.findById(dto.getAvaliacao().getIdavaliacao()));
			} else {
				dto.getAvaliacao().persist();
				bam.setAvaliacao(dto.getAvaliacao());
			}
		}
		if (dto.getPaciente() != null) {
			if (dto.getPaciente().getIdpaciente() > 0) {
				recusarCadastroUnificado(dto.getPaciente().getIdpaciente());
				bam.setPaciente(br.com.iv.entity.Pacientes.findById(dto.getPaciente().getIdpaciente()));
			} else {
				if (dto.getPaciente().getNascimento() == null || dto.getPaciente().getNascimento().isBlank()) {
					throw new jakarta.ws.rs.BadRequestException("Campo 'nascimento' do paciente é obrigatório.");
				}
				dto.getPaciente().persist();
				bam.setPaciente(dto.getPaciente());
			}
		}
		if (dto.getTratamento() != null) {
			if (dto.getTratamento().getIdtratamento() > 0) {
				bam.setTratamento(br.com.iv.entity.Tratamento.findById(dto.getTratamento().getIdtratamento()));
			} else {
				dto.getTratamento().persist();
				bam.setTratamento(dto.getTratamento());
			}
		}
		if (dto.getServidor() != null) {
			if (dto.getServidor().getIdservidor() > 0) {
				bam.setServidor(br.com.iv.entity.Servidor.findById(dto.getServidor().getIdservidor()));
			} else {
				dto.getServidor().persist();
				bam.setServidor(dto.getServidor());
			}
		}
		bam.setData(Date.from(dto.getData().toInstant().plus(1, ChronoUnit.DAYS))); // Data acrescida de 1 dia
		bam.setHora(dto.getHora());
		if (dto.getProntuario() != null && dto.getProntuario().getIdprontuario() > 0) {
			bam.setProntuario(br.com.iv.entity.Prontuario.findById(dto.getProntuario().getIdprontuario()));
		} else {
			bam.setProntuario(null);
		}
		bam.setTransferido(dto.isTransferido());
		bam.setComentariotranferencia(dto.getComentariotranferencia());
		bam.setControle(dto.getControle());
		bam.setDarAlta(dto.isDarAlta());
		bam.setInternar(dto.isInternar());
		bam.setPrintTriagem(dto.getPrintTriagem());
		bam.setNAtendente(dto.getNAtendente());
		bam.setCor(dto.getCor());
		bam.setReceita(dto.getReceita());
		bam.setEmatendimento(dto.isEmatendimento());
		bam.setUnidade(dto.getUnidade());
		bam.persist();
		return bam;
	}

	// Alterar
	@Transactional
	public void atualizarBam(int id, BamDto dto) {
		Optional<Bam> BamOp = Bam.findByIdOptional(id);
		Bam bam = BamOp.orElseThrow(() -> new NullPointerException("Bam não foi encontrado..."));

		bam.setRegistro(dto.getRegistro());
		bam.setAvaliacao(dto.getAvaliacao());
		bam.setPaciente(dto.getPaciente());
		bam.setTratamento(dto.getTratamento());
		bam.setServidor(dto.getServidor());
		// bam.setProntuario(dto.getProntuario());
		bam.setTransferido(dto.isTransferido());
		bam.setComentariotranferencia(dto.getComentariotranferencia());
		bam.setControle(dto.getControle());
		bam.setDarAlta(dto.isDarAlta());
		bam.setInternar(dto.isInternar());
		bam.setPrintTriagem(dto.getPrintTriagem());
		bam.setNAtendente(dto.getNAtendente());
		bam.setCor(dto.getCor());
		bam.setReceita(dto.getReceita());
		bam.setEmatendimento(dto.isEmatendimento());
		bam.setUnidade(dto.getUnidade());

		bam.persist();

	}
	
	@Transactional
	public List<Bam> filtarMapaGrafico(LocalDate d1, LocalDate d2, String unidade) {

		List<Bam> bamOP = bamRepository.filtarMapaGrafico(d1, d2, unidade);
		if (bamOP.isEmpty()) {
			System.out.println("Não há pacientes para mapear ...");
		}
		return bamOP;
	}


}
