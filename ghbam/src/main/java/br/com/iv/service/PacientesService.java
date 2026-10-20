package br.com.iv.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import br.com.iv.aps.ApsValidacao;
import br.com.iv.dto.AtendMedicoDTO;
import br.com.iv.dto.MunicipioCountDTO;
import br.com.iv.dto.PacienteDto;
import br.com.iv.dto.PacienteResultadoExameDto;
import br.com.iv.dto.PacientesAtendimentoDto;
import br.com.iv.dto.PacientesBamDto;
import br.com.iv.dto.PacientesDto;
import br.com.iv.dto.PacientesNirDto;
import br.com.iv.entity.Pacientes;
import br.com.iv.repository.PacientesRepository;
import br.com.iv.util.UnidadeUtil;
import br.com.iv.util.DateUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class PacientesService implements Serializable {

	private static final long serialVersionUID = 1L;

	@Inject
	PacientesRepository pacientesRepository;

	@Inject
	ProntuarioDefinitivoService prontuarioDefinitivoService;

	// Coordenada do endereco calculada depois do commit (ver GeocodificacaoPacienteService).
	@Inject
	Event<GeocodificacaoPacienteService.Pedido> geocodificarEndereco;

	// Listar tudo
	public List<Pacientes> listarPacientes() {
		return Pacientes.listAll();
	}

	@Transactional
	public Pacientes PacientePorId(int idPaciente) {
		return pacientesRepository.filtrarPorUnicoPacienteId(idPaciente);
	}

	// Fechar todos os bams do paciente antes de abrir um novo
	@Transactional
	public void fecharBamAbertoDoPaciente(int idPaciente) {
		pacientesRepository.fecharTodosBamAbertoDoPaciente(idPaciente);
	}

	// Filtar por unidade o pacientes para classificação de risco

	public List<Pacientes> locaisDeTriagem(String unidade) {
		List<Pacientes> pacientesOP = pacientesRepository.locaisDeTriagem(unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes para triagem...");
		}
		return pacientesOP;
	}

	public List<Pacientes> localizarPeloBam(int bam) {
		List<Pacientes> pacientesOP = pacientesRepository.localizarPeloBam(bam);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes");
		}
		return pacientesOP;
	}

	@Transactional
	public List<PacienteDto> locaisDeTriagem2(String unidade, String setor) {
		if (setor.equals("null") || setor.equals("") || setor.equals("nada")) {
			setor = "%";
		}
		List<PacienteDto> pacientesOP = pacientesRepository.locaisDeTriagem2(unidade, setor);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes para triagem...");
			
		}
		return pacientesOP;
	}

	@Transactional
	public List<PacientesAtendimentoDto> medicoAtendendo(String nome, String unidade) {
		List<PacientesAtendimentoDto> pacientesOP = pacientesRepository.ordem_atendimento_do_medico(nome, unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes em atendimento");
		}
		return pacientesOP;
	}

	@Transactional
	public List<AtendMedicoDTO> medicoAguardando(String unidade) {
		List<AtendMedicoDTO> pacientesOP = pacientesRepository.ordem_aguardando_do_medico(unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes em atendimento");
		}
		return pacientesOP;
	}
	
	// Atualiza idade na reimpressão do bam
	// Sem @Transactional aqui: cada tentativa abre sua própria transação via
	// REQUIRES_NEW no repositório, evitando reutilizar uma transação já marcada
	// para rollback após deadlock.
	public void alterarAIdade(String nomePaciente) {
		int maxTentativas = 5;
		for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
			try {
				pacientesRepository.alterarAIdade(nomePaciente);
				return; // sucesso
			} catch (OptimisticLockException | PessimisticLockException | LockTimeoutException e) {
				if (tentativa == maxTentativas) {
					throw e; // esgotou as tentativas
				}
				// Backoff exponencial com jitter aleatório para reduzir colisões entre
				// requisições concorrentes e evitar que todas reintentem ao mesmo tempo
				long baseMs = 50L * (1L << (tentativa - 1)); // 50, 100, 200, 400 ms
				long jitterMs = ThreadLocalRandom.current().nextLong(0, baseMs + 1);
				try {
					Thread.sleep(baseMs + jitterMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Interrompido durante retry de alterarAIdade", ie);
				}
			}
		}
	}

	// Atualiza idade na triagem com retry em caso de deadlock
	public void alterarAIdadeNaTriagem(String unidade) {
		int maxTentativas = 5;
		for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
			try {
				pacientesRepository.alterarAIdadeNaTriagem(unidade);
				return; // sucesso
			} catch (OptimisticLockException | PessimisticLockException | LockTimeoutException e) {
				if (tentativa == maxTentativas) {
					throw e; // esgotou as tentativas
				}
				long baseMs = 50L * (1L << (tentativa - 1)); // 50, 100, 200, 400 ms
				long jitterMs = ThreadLocalRandom.current().nextLong(0, baseMs + 1);
				try {
					Thread.sleep(baseMs + jitterMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new RuntimeException("Interrompido durante retry de alterarAIdadeNaTriagem", ie);
				}
			}
		}
	}

	// Salvar
	@Transactional
	public Pacientes salvarPaciente(PacientesDto dto) {
		return salvarPaciente(dto, "");
	}

	/** {@code unidade}: sigla da sessao - municipio e coordenada padrao vem da unidade cadastrada. */
	@Transactional
	public Pacientes salvarPaciente(PacientesDto dto, String unidade) {
		validarCnsInformado(dto == null ? null : dto.getCns());
		validarDuplicidadeCadastro(dto);

		Pacientes pacientes = new Pacientes();
		pacientes.setNome(dto.getNome());
		pacientes.setEndereco(dto.getEndereco());
		pacientes.setIdade(dto.getIdade());
		pacientes.setCep(dto.getCep());
		pacientes.setNascimento(DateUtils.toYMD(dto.getNascimento()));
		pacientes.setProfissao(dto.getProfissao());
		pacientes.setSexo(dto.getSexo());
		pacientes.setEtnia(dto.getEtnia());
		pacientes.setMae(dto.getMae());
		pacientes.setPai(dto.getPai());
		pacientes.setCns(dto.getCns());
		pacientes.setCpf(dto.getCpf());
		pacientes.setRg(dto.getRg());
		pacientes.setEcivil(dto.getEcivil());
		pacientes.setNaturalidade(dto.getNaturalidade());
		pacientes.setEndereco(dto.getEndereco());
		pacientes.setNumero(dto.getNumero());
		pacientes.setBairro(dto.getBairro());
		pacientes.setCep(dto.getCep());
		pacientes.setEstado(dto.getEstado());
		pacientes.setTelefone(dto.getTelefone());
		pacientes.setSangue(dto.getSangue());
		pacientes.setMunicipio(dto.getMunicipio());
		pacientes.setIdade(dto.getIdade());
		pacientes.setProntuario(dto.getProntuario());
		pacientes.setLatitude(dto.getLatitude());
		pacientes.setLongitude(dto.getLongitude());
		pacientes.setPacienteespecial(dto.getPacienteespecial());
		pacientes.setSic(dto.isSic());
		pacientes.setData(dto.getData());
		pacientes.setHora(dto.getHora());
		pacientes.setTempcod(dto.getTempcod());
		GeocodificacaoPacienteService.PadraoUnidade padrao = GeocodificacaoPacienteService.padraoDaUnidade(unidade);
		GeocodificacaoPacienteService.completarMunicipio(pacientes, padrao);
		// Sem coordenada real vinda da tela: centro do bairro/municipio (tabela local, sem internet)
		// ou, sem nada conhecido, o ponto da unidade.
		if (GeocodificacaoPacienteService.semLocalizacaoPropria(pacientes, padrao)) {
			GeocodificacaoPacienteService.aplicarCoordenadaLocal(pacientes, padrao);
		}
		pacientes.persist();
		// Refinamento pela rua via Nominatim, se configurado (ghbam.geocodificacao.url).
		geocodificarEndereco.fire(GeocodificacaoPacienteService.pedidoDe(pacientes,
				!GeocodificacaoPacienteService.semLocalizacaoPropria(pacientes, padrao)));
		return pacientes;
	}

	private void validarDuplicidadeCadastro(PacientesDto dto) {
		if (dto == null) {
			return;
		}

		Pacientes porCpf = pacientesRepository.buscarPorCpfNormalizado(dto.getCpf());
		if (porCpf != null) {
			throw conflitoPaciente("CPF", porCpf);
		}

		Pacientes porCns = pacientesRepository.buscarPorCnsNormalizado(dto.getCns());
		if (porCns != null) {
			throw conflitoPaciente("CNS", porCns);
		}
	}

	/**
	 * Edicao: cadastrar sem documento e incluir depois o CPF/CNS de outro paciente
	 * gerava cadastro duplicado. So confere quando o campo muda, para nao travar
	 * edicoes de cadastros legados que ja estao duplicados.
	 */
	private void validarDuplicidadeNaEdicao(Pacientes atual, PacientesDto dto) {
		if (dto == null) {
			return;
		}
		if (!digitos(atual.getCpf()).equals(digitos(dto.getCpf()))) {
			Pacientes porCpf = pacientesRepository.buscarPorCpfNormalizado(dto.getCpf());
			if (porCpf != null && porCpf.getIdpaciente() != atual.getIdpaciente()) {
				throw conflitoPaciente("CPF", porCpf);
			}
		}
		if (!digitos(atual.getCns()).equals(digitos(dto.getCns()))) {
			Pacientes porCns = pacientesRepository.buscarPorCnsNormalizado(dto.getCns());
			if (porCns != null && porCns.getIdpaciente() != atual.getIdpaciente()) {
				throw conflitoPaciente("CNS", porCns);
			}
		}
	}

	private static String digitos(String valor) {
		return valor == null ? "" : valor.replaceAll("\\D", "");
	}

	/**
	 * CNS e opcional (vazio passa), mas quando informado precisa ser valido:
	 * barra zeros, texto ("NAO TROUXE", "NI") e digito verificador errado.
	 */
	private static void validarCnsInformado(String cns) {
		if (cns == null || cns.isBlank() || ApsValidacao.cnsValido(cns)) {
			return;
		}
		throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
				.entity(Map.of(
						"erro", "CNS invalido. Informe os 15 digitos do cartao ou deixe o campo em branco.",
						"campo", "CNS"))
				.build());
	}

	private static String textoCns(String valor) {
		return valor == null ? "" : valor.trim().toUpperCase();
	}

	private WebApplicationException conflitoPaciente(String campo, Pacientes paciente) {
		String mensagem = String.format(
				"Paciente ja cadastrado com este %s. Carregue o cadastro existente para abrir um novo BAM. Paciente: %s | ID: %d | Prontuario: %s",
				campo,
				paciente.getNome(),
				paciente.getIdpaciente(),
				paciente.getProntuario());
		return new WebApplicationException(Response.status(Response.Status.CONFLICT)
				.entity(Map.of(
						"erro", mensagem,
						"campo", campo,
						"idpaciente", paciente.getIdpaciente(),
						"nome", paciente.getNome() == null ? "" : paciente.getNome(),
						"prontuario", paciente.getProntuario() == null ? "" : paciente.getProntuario()))
				.build());
	}

	// Filtar por nome
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlocalizarPaciente(String nome, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.filtarPorPaciente(nome);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}

	@Transactional
	public List<Pacientes> localizarPacientesComBamRecente(String nome, String unidade) {
		return pacientesRepository.filtrarPacientesComBamRecente(
				nome, UnidadeUtil.normalizar(unidade));
	}

	@Transactional
	public Pacientes xlocalizarPacientecpf(String cpf, PacientesDto dto) {
		new Pacientes();
		Pacientes pacientesOp = pacientesRepository.filtarPorPacientecpf(cpf);
		if (pacientesOp == null) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}
	
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlocalizarPacienteRn(String nome, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.filtarPorPacienteRn(nome);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}
	
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlocalizarPacienteRnVerificar(String nomeDaMae, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.filtarPorPacienteRnVerificar(nomeDaMae);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}
	
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlocalizarPacienteNir(String nome, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.filtrarPorPacienteNir(nome);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}

	// Filtar por nome e mae
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlocalizarPacienteEMae(String nome, String mae, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.filtarPorPacienteEMae(nome, mae);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}

	@Transactional
	public List<Pacientes> localizarPorMae(String mae) {
		return pacientesRepository.filtrarPorMae(mae);
	}

	
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlistarPacientesNome(String nome, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.xlistarPacientesNome(nome);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}
	
	
	// Filtar por cpf
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlocalizarCPF(String cpf, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.filtarPorCPF(cpf);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}

	// Filtar por cpf
	@SuppressWarnings("static-access")
	@Transactional
	public List<Pacientes> xlocalizarControle(String controle, PacientesDto dto) {
		new Pacientes();
		List<Pacientes> pacientesOp = pacientesRepository.filtarPorControle(controle);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		return pacientesOp;
	}

	// Alterar
	@SuppressWarnings("static-access")
	@Transactional
	public void atualizarPaciente(int id, PacientesDto dto) {
		atualizarPaciente(id, dto, "");
	}

	@SuppressWarnings("static-access")
	@Transactional
	public void atualizarPaciente(int id, PacientesDto dto, String unidade) {
		Pacientes pacientes = new Pacientes();
		Optional<Pacientes> pacientesOp = pacientes.findByIdOptional(id);
		if (pacientesOp.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		pacientes = pacientesOp.get();
		// Cadastro legado com CNS invalido nao trava outras edicoes: so valida quando o CNS muda.
		if (!textoCns(pacientes.getCns()).equals(textoCns(dto.getCns()))) {
			validarCnsInformado(dto.getCns());
		}
		validarDuplicidadeNaEdicao(pacientes, dto);
		String enderecoAntes = GeocodificacaoPacienteService.chaveEndereco(pacientes);
		pacientes.setNome(dto.getNome());
		pacientes.setEndereco(dto.getEndereco());
		pacientes.setIdade(dto.getIdade());
		pacientes.setCep(dto.getCep());
		pacientes.setNascimento(DateUtils.toYMD(dto.getNascimento()));
		pacientes.setProfissao(dto.getProfissao());
		pacientes.setSexo(dto.getSexo());
		pacientes.setEtnia(dto.getEtnia());
		pacientes.setMae(dto.getMae());
		pacientes.setPai(dto.getPai());
		pacientes.setCns(dto.getCns());
		pacientes.setCpf(dto.getCpf());
		pacientes.setRg(dto.getRg());
		pacientes.setEcivil(dto.getEcivil());
		pacientes.setNaturalidade(dto.getNaturalidade());
		pacientes.setEndereco(dto.getEndereco());
		pacientes.setNumero(dto.getNumero());
		pacientes.setBairro(dto.getBairro());
		pacientes.setCep(dto.getCep());
		pacientes.setEstado(dto.getEstado());
		pacientes.setTelefone(dto.getTelefone());
		pacientes.setSangue(dto.getSangue());
		pacientes.setMunicipio(dto.getMunicipio());
		pacientes.setIdade(dto.getIdade());
		pacientes.setProntuario(dto.getProntuario());
		pacientes.setLatitude(dto.getLatitude());
		pacientes.setLongitude(dto.getLongitude());
		pacientes.setPacienteespecial(dto.getPacienteespecial());
		pacientes.setSic(dto.isSic());
		pacientes.setTempcod(dto.getTempcod());
		GeocodificacaoPacienteService.PadraoUnidade padrao = GeocodificacaoPacienteService.padraoDaUnidade(unidade);
		GeocodificacaoPacienteService.completarMunicipio(pacientes, padrao);
		// Recalcula quando o endereco mudou ou quando ainda nao tem localizacao propria.
		boolean enderecoMudou = !enderecoAntes.equals(GeocodificacaoPacienteService.chaveEndereco(pacientes));
		boolean recalcular = enderecoMudou || GeocodificacaoPacienteService.semLocalizacaoPropria(pacientes, padrao);
		if (recalcular) {
			if (enderecoMudou) {
				// A coordenada antiga era do endereco anterior: nao pode ser mantida como "real".
				pacientes.setLatitude(GeocodificacaoPacienteService.LATITUDE_PADRAO);
				pacientes.setLongitude(GeocodificacaoPacienteService.LONGITUDE_PADRAO);
			}
			GeocodificacaoPacienteService.aplicarCoordenadaLocal(pacientes, padrao);
		}
		pacientes.persist();
		if (recalcular) {
			geocodificarEndereco.fire(GeocodificacaoPacienteService.pedidoDe(pacientes,
					!GeocodificacaoPacienteService.semLocalizacaoPropria(pacientes, padrao)));
		}
	}



	public void removePaciente(Long id) {
		Optional<Pacientes> pacientesOP = Pacientes.findByIdOptional(id);
		if (pacientesOP.isEmpty()) {
			System.out.println("Paciente não foi encontrado...");
		}

		Pacientes pacientes = pacientesOP.get();
		pacientes.delete();

	}

	// ----------------------------- NIR -----------------------
	@Transactional
	public List<PacientesNirDto> filtragemParaNir(String nome, String unidade, String tipo) {

		List<PacientesNirDto> pacientesOP = pacientesRepository.filtragemParaNir(
				nome, UnidadeUtil.normalizar(unidade), tipo);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}

	@Transactional
	public List<PacientesNirDto> filtragemParaNirPainel(String unidade) {

		List<PacientesNirDto> pacientesOP = pacientesRepository.filtragemParaNirPainel(unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}

	
	@Transactional
	public List<PacientesNirDto> filtragemParaNirResturarInternacao(String nome, String unidade, String tipo) {

		List<PacientesNirDto> pacientesOP = pacientesRepository.filtragemParaNirResturarInternacao(nome, unidade, tipo);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}
	
	
	
	
	@Transactional
	public List<PacientesNirDto> filtragemParaNir2(String nome, String unidade, String local) {
		List<PacientesNirDto> pacientesOP = pacientesRepository.filtragemParaNir2(nome, unidade, local);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}

	@Transactional
	public List<PacientesNirDto> filtragemParaNirInternado(String nome, String unidade) {

		List<PacientesNirDto> pacientesOP = pacientesRepository.filtragemParaNirInternado(nome, unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}

	@Transactional
	public List<PacientesNirDto> filtragemParaNirInternadoTodos(String nome, String unidade) {

		List<PacientesNirDto> pacientesOP = pacientesRepository.filtragemParaNirInternadoTodos(nome, unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}	
	
	
	@Transactional
	public List<PacientesNirDto> filtragemParaNirPorId(int id, String unidade) {

		List<PacientesNirDto> pacientesOP = pacientesRepository.filtragemParaNirPorId(id, unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}

	@Transactional
	public void alterarProntuario(int id, String prontuario) {
		pacientesRepository.alterarProntuario(id, prontuario);
		// "Gerar prontuario" do NIR: internacao e leito abertos passam a usar o numero novo.
		if (!ProntuarioDefinitivoService.pendente(prontuario)) {
			prontuarioDefinitivoService.propagar(id, prontuario.trim());
		}
	}
	
	@Transactional
	public List<PacienteResultadoExameDto> listaResultadoExamesLaboratorio(String nome, String local, String unidade) {
		List<PacienteResultadoExameDto> pacientesOP = pacientesRepository.listaResultadoExamesLaboratorio(nome, local, unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes com resultado de exames realizados...");
		}
		return pacientesOP;
	}

	public List<MunicipioCountDTO> filtarPorPacienteMunicipio(String unidade, String d1, String d2) {
	    List<Object[]> results = pacientesRepository.filtarPorPacienteMunicipio(unidade, d1, d2);
	    List<MunicipioCountDTO> dtoList = new ArrayList<>();

	    for (Object[] row : results) {
	        String municipio = (String) row[0];
	        Long total = ((Number) row[1]).longValue();
	        dtoList.add(new MunicipioCountDTO(municipio, total));
	    }

	    return dtoList;
	}
	
	//------------------------------------ Correção de bam --------------------------------------
	@Transactional
	public List<PacientesNirDto> corrigirbam(String bam, String unidade) {

		List<PacientesNirDto> pacientesOP = pacientesRepository.corrigirbam(bam, unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}
	
	@Transactional
	public List<PacientesBamDto> corrigirbam2(int id, String bam, String unidade) {

		List<PacientesBamDto> pacientesOP = pacientesRepository.corrigirbam2(id, bam, unidade);
		if (pacientesOP.isEmpty()) {
			System.out.println("Não há pacientes ...");
		}
		return pacientesOP;
	}
	
	@Transactional
	public void opcao1(int bamgravado, int idpaciente1, int bamultimo, String prontuario, int idregistro, int idtratado, String controle, String unidade) {
		pacientesRepository.opcao1( bamgravado,  idpaciente1,  bamultimo,  prontuario,  idregistro,  idtratado,  controle,  unidade);
	}

	@Transactional
	public void opcao2( int bamultimo) {
		pacientesRepository.opcao2(bamultimo);
	}
	@Transactional
	public void opcao3( int bamultimo) {
		pacientesRepository.opcao3(bamultimo);
	}
	@Transactional
	public void opcao4( int bamultimo) {
		pacientesRepository.opcao4(bamultimo);
	}
	@Transactional
	public void opcao5(int bamultimo, int idpac, String unidade) {
		pacientesRepository.opcao5( bamultimo,  idpac,  unidade);
	}

	@Transactional
	public void opcao7( int bamultimo) {
		pacientesRepository.opcao7(bamultimo);
	}

	@Transactional
	public void opcao8(int bamultimo, String unidade) {
		pacientesRepository.opcao8(bamultimo, unidade);
	}

	@Transactional
	public void opcao9(int bamultimo, String unidade) {
		pacientesRepository.opcao9(bamultimo, unidade);
	}

	@Transactional
	public int opcao10(int bamultimo, String unidade) {
		return pacientesRepository.opcao10(bamultimo, unidade);
	}

	@Transactional
	public void corrigirAntigo() {
		pacientesRepository.corrigirAntigo();
		
	}
}
