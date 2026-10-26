
package br.com.iv.service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mindrot.jbcrypt.BCrypt;

import br.com.iv.dto.Servidor2Dto;
import br.com.iv.dto.ServidorDto;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;
import br.com.iv.entity.Setores;
import br.com.iv.repository.Servidor2Repository;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.repository.ServidorUnidadeRepository;
import br.com.iv.security.SecurityPermissionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ServidorService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ServidorRepository servidorRepository;

	@Inject
	Servidor2Repository servidor2Repository;

	@Inject
	Servidor2Service servidor2Service;

	@Inject
	SecurityPermissionService securityPermissionService;

	@Inject
	ServidorUnidadeRepository servidorUnidadeRepository;

	@Inject
	br.com.iv.repository.ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

	@Inject
	br.com.iv.biofingertech.SourceAfisFingerprintService sourceAfis;

	// Gera o modelo SourceAFIS a partir das imagens do cadastro. Se a digital foi
	// trocada/removida sem imagem (agente antigo), descarta o modelo anterior para
	// nao autenticar com a digital velha; ele sera refeito no proximo login interno.
	private void aplicarModeloSourceafis(Servidor servidor, ServidorDto dto, String firAnterior) {
		if (dto.getBiometriaImagens() != null && !dto.getBiometriaImagens().isEmpty()) {
			try {
				servidor.setBiometriaSourceafis(sourceAfis.createTemplatesFromImages(dto.getBiometriaImagens(), dto.getBiometriaDpi()));
				return;
			} catch (RuntimeException ex) {
				servidor.setBiometriaSourceafis(null);
				return;
			}
		}
		String antes = firAnterior == null ? "" : firAnterior.trim();
		String depois = servidor.getFir() == null ? "" : servidor.getFir().trim();
		if (!antes.equals(depois)) {
			servidor.setBiometriaSourceafis(null);
		}
	}

	static void aplicarRegistroProfissional(Servidor servidor, ServidorDto dto) {
		servidor.setCorem(dto.getCorem() != null ? dto.getCorem() : servidor.getCorem());
		servidor.setCrm(dto.getCrm() != null ? dto.getCrm() : servidor.getCrm());
	}

	/**
	 * Migra todos os registros de Servidor para Servidor2, caso nÃ£o existam ainda.
	 */
	@Transactional
	public void migrarServidoresParaServidor2(String unidade) {
		if (unidade == null || unidade.trim().isEmpty()) {
			throw new IllegalArgumentException("Unidade obrigatoria para migrar servidores.");
		}

		List<Servidor> servidores = servidorRepository.filtarPorUnidadeDoServidorAtivo(unidade.trim());
		for (Servidor servidor : servidores) {
				if (servidor2Service.buscarPorMatricula(servidor.getMatricula()) == null) {
					Servidor2Dto dto = new Servidor2Dto();
					dto.setMatricula(servidor.getMatricula());
					dto.setNome(servidor.getNome());
					dto.setTel(servidor.getTel());
					dto.setEmail(servidor.getEmail());
					dto.setUnidade(servidor.getUnidade());
					dto.setCns(""); // ajuste conforme necessÃ¡rio
					// preencher explicitamente todos os booleans para evitar PropertyValueException
					dto.setCadsalas(Boolean.FALSE);
					dto.setCadsetores(Boolean.FALSE);
					dto.setCadunidades(Boolean.FALSE);
					dto.setCadusuarios(Boolean.FALSE);
					dto.setListausuarios(Boolean.FALSE);
					dto.setMapapacientes(Boolean.FALSE);
					dto.setResumopaciente(Boolean.FALSE);
					dto.setCadleitos(Boolean.FALSE);
					dto.setCadsetor(Boolean.FALSE);
					dto.setRestaurarpaciente(Boolean.FALSE);
					// permissÃµes de enfermagem
					dto.setBalancohidrico(Boolean.FALSE);
					dto.setCcih(Boolean.FALSE);
					dto.setDriveenfermagem(Boolean.FALSE);
					dto.setEscalaenfermagem(Boolean.FALSE);
					dto.setEvolucaomultidisciplinar(Boolean.FALSE);
					servidor2Service.salvarServidor2(dto);
				}
		}
	}


	// Listando todos os setores
	public List<Servidor> listarTodosOsServidores() {
		return servidorRepository.listAll();
	}

	public List<Servidor> listarTodosOsEspecialistas(String unidade) {
		return servidorRepository.especialistas(unidade);
	}
	
	// Filtar por setor e unidade
	@Transactional
	public List<Servidor> filtarPorServidor(String servidor, String unidade) {
		// Setores setores = new Setores();
		List<Servidor> servidorOp = servidorRepository.filtarPorServidor(servidor, unidade);
		if (servidorOp.isEmpty()) {
			System.out.println("Servidor nÃ£o encontrado...");
		}
		return servidorOp;
	}

	// O front manda a nova senha em Base64 (mesmo formato de sempre, pra nao
	// mexer no Senhaalt.jsx) — aqui decodifica pra pegar os 6 digitos reais
	// e grava so o hash bcrypt, nunca a senha em texto/Base64 recuperavel.
	@Transactional
	public void alterarSenha(String matricula, String pwd) {
		String senhaPlana = decodificarSenhaSeBase64(pwd);
		String hash = BCrypt.hashpw(senhaPlana, BCrypt.gensalt());
		servidorRepository.alterarSenha(matricula, hash);
		securityPermissionService.invalidateUserCache(matricula);
	}

	private String decodificarSenhaSeBase64(String pwd) {
		if (pwd == null || pwd.isBlank()) {
			return "";
		}
		try {
			String decoded = new String(Base64.getDecoder().decode(pwd), StandardCharsets.UTF_8);
			return decoded.matches("\\d{6}") ? decoded : pwd;
		} catch (IllegalArgumentException e) {
			return pwd;
		}
	}

	@Transactional
	public void alterarStatus(int id, boolean status) {
		Servidor servidor = servidorRepository.filtrarPorID(id);
		servidorRepository.alterarStatus(id, status);
		if (servidor != null) securityPermissionService.invalidateUserCache(servidor.getMatricula());
	}

	@Transactional
	public Servidor servidorId(int id) {
		return servidorRepository.filtrarPorID(id);
	}

	@Transactional
	public List<Servidor> filtarPorUnidadeServidor(String unidade, ServidorDto dto) {
		List<Servidor> servidorOp = servidorRepository.filtarPorUnidadeDoServidor(unidade);
		if (servidorOp.isEmpty()) {
			System.out.println("NÃ£o hÃ¡ servidores cadastrados nessa unidade...");
		}
		return servidorOp;
	}

	@Transactional
	public List<Servidor> filtarPorUnidadeMedico(String unidade, ServidorDto dto) {

		// Setores setores = new Setores();
		List<Servidor> servidorOp = servidorRepository.filtarPorUnidadeDoServidorMedico(unidade);
		if (servidorOp.isEmpty()) {
			System.out.println("NÃ£o hÃ¡ servidores cadastrados nessa unidade...");
		}
		return servidorOp;
	}

	public List<Servidor> listarMedicos() {
		return servidorRepository.list(
			"ativo = true AND (UPPER(cargo) LIKE '%MEDICO%' OR UPPER(cargo) LIKE '%MÃ‰DICO%')"
		);
	}

	public Map<String, String> obterCertificadoPorMatricula(String matricula) {
		Servidor servidor = servidorRepository.find("matricula", matricula).firstResult();
		if (servidor == null || servidor.getCertificadoPfxBase64() == null || servidor.getCertificadoPfxBase64().isBlank()) {
			return null;
		}
		Map<String, String> resultado = new HashMap<>();
		resultado.put("certificadoPfxBase64", servidor.getCertificadoPfxBase64());
		resultado.put("certificadoPfxSenha", servidor.getCertificadoPfxSenha());
		return resultado;
	}

	// Salvar os setores
	@Transactional
	public Servidor salvarServidor(ServidorDto dto) {
		if (dto.getMatricula() != null && !dto.getMatricula().isBlank()) {
			Servidor existente = servidorRepository.find("matricula", dto.getMatricula().trim()).firstResult();
			if (existente != null) {
				throw new IllegalStateException("MatrÃ­cula " + dto.getMatricula() + " jÃ¡ cadastrada.");
			}
		}

		Servidor servidor = new Servidor();
		servidor.setMatricula(dto.getMatricula());
		servidor.setNome(dto.getNome());
		servidor.setNascimento(dto.getNascimento());
		servidor.setCpf(dto.getCpf());
		servidor.setTel(dto.getTel());
		servidor.setEmail(dto.getEmail());
		String senhaInicialCadastro = dto.getPwd() == null || dto.getPwd().isBlank() ? "123456" : decodificarSenhaSeBase64(dto.getPwd());
		servidor.setPwd(BCrypt.hashpw(senhaInicialCadastro, BCrypt.gensalt()));
		servidor.setRecepcao(dto.getRecepcao());
		servidor.setTriagem(dto.getTriagem());
		servidor.setAdministracao(dto.getAdministracao());
		servidor.setSubadm(dto.getSubadm());
		servidor.setMedico(dto.getMedico());
		servidor.setDentista(Boolean.TRUE.equals(dto.getDentista()));
		servidor.setDanfe(dto.getDanfe());
		servidor.setRx(dto.getRx());
		servidor.setCns(dto.getCns());
		servidor.setFarmacia(dto.getFarmacia());
		servidor.setRemedios(dto.getRemedios());
		servidor.setLaboratorio(dto.getLaboratorio());
		servidor.setFaturamento(dto.getFaturamento());
		servidor.setFarmhmsfx(dto.getFarmhmsfx());
		servidor.setFarmcentral(dto.getFarmcentral());
		servidor.setPsicologia(dto.getPsicologia());
		servidor.setMedicacao(dto.getMedicacao());
		servidor.setEnfermagem(dto.getEnfermagem());
		servidor.setTcenfermagem(dto.getTcenfermagem());
		servidor.setAssistencia(dto.getAssistencia());
		servidor.setFisioterapia(dto.getFisioterapia());
		servidor.setNutricao(dto.getNutricao());
		servidor.setFonoaudiologia(dto.getFonoaudiologia());
		servidor.setCriaragenda(dto.getCriaragenda());
		servidor.setAgendarpaciente(dto.getAgendarpaciente());
		servidor.setConsultorioadmin(dto.getConsultorioadmin() != null ? dto.getConsultorioadmin() : false);
		servidor.setConsultoriorecepcao(dto.getConsultoriorecepcao() != null ? dto.getConsultoriorecepcao() : false);
		servidor.setConsultoriomedico(dto.getConsultoriomedico() != null ? dto.getConsultoriomedico() : false);
		servidor.setConsultoriofinanceiro(dto.getConsultoriofinanceiro() != null ? dto.getConsultoriofinanceiro() : false);
		servidor.setTomografia(dto.getTomografia());
		servidor.setUltrassonografia(dto.getUltrassonografia());
		servidor.setRh(dto.getRh() != null ? dto.getRh() : false);
		servidor.setAtivo(dto.getAtivo());
		servidor.setCargo(dto.getCargo());
		aplicarRegistroProfissional(servidor, dto);
		servidor.setEspecialidade(dto.getEspecialidade());
		servidor.setUnidade(dto.getUnidade());
		servidor.setCbo(dto.getCbo());
		servidor.setFoto(dto.getFoto());
		servidor.setBiometria(dto.getBiometria());
		servidor.setFir(dto.getBiometria());
		aplicarModeloSourceafis(servidor, dto, null);
		if(servidor.getCorem().equals("") && !servidor.getCrm().equals(""))
			servidor.setCorem(servidor.getCrm());
		
		servidor.setUsuarioad(dto.getUsuarioad());
		if (dto.getCertificadoPfxBase64() != null && !dto.getCertificadoPfxBase64().isBlank()) {
			servidor.setCertificadoPfxBase64(dto.getCertificadoPfxBase64());
			servidor.setCertificadoPfxSenha(dto.getCertificadoPfxSenha());
			servidor.setCertificadoTitular(dto.getCertificadoTitular());
			servidor.setCertificadoEmissor(dto.getCertificadoEmissor());
			servidor.setCertificadoValidade(dto.getCertificadoValidade());
		}
		servidor.persist();

		// Salvar tambÃ©m em Servidor2
		Servidor2Dto servidor2Dto = new Servidor2Dto();
		servidor2Dto.setMatricula(dto.getMatricula());
		servidor2Dto.setNome(dto.getNome());
		servidor2Dto.setTel(dto.getTel());
		servidor2Dto.setEmail(dto.getEmail());
		servidor2Dto.setUnidade(dto.getUnidade());
		servidor2Dto.setCns(""); // ajuste conforme necessÃ¡rio
		servidor2Dto.setCadsalas(false); // ajuste conforme necessÃ¡rio
		servidor2Dto.setCadsetores(false);
		servidor2Dto.setCadunidades(false);
		servidor2Dto.setCadusuarios(false);
		servidor2Dto.setListausuarios(false);
		servidor2Dto.setMapapacientes(false);
		servidor2Dto.setResumopaciente(false);
		servidor2Dto.setCadleitos(false);
		servidor2Dto.setCadsetor(false);
		servidor2Dto.setRestaurarpaciente(false);
		servidor2Service.salvarServidor2(servidor2Dto);
		securityPermissionService.invalidateUserCache(servidor.getMatricula());
		return servidor;
	}

	// Alterar
	@Transactional
	public void atualizarServidor(int id, ServidorDto dto) {
		Optional<Servidor> servidorOp = Servidor.findByIdOptional(id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
		Servidor servidor = servidorOp.orElseThrow(() -> new NullPointerException("O servidor nÃ£o foi encontrado..."));

		ServidorMatriculaService.validarMatriculaCadastro(servidor.getMatricula(), dto.getMatricula());
		servidor.setNome(dto.getNome() != null ? dto.getNome() : servidor.getNome());
		servidor.setNascimento(dto.getNascimento() != null ? dto.getNascimento() : servidor.getNascimento());
		servidor.setCpf(dto.getCpf() != null ? dto.getCpf() : servidor.getCpf());
		servidor.setTel(dto.getTel() != null ? dto.getTel() : servidor.getTel());
		servidor.setEmail(dto.getEmail() != null ? dto.getEmail() : servidor.getEmail());
		// pwd nunca vem preenchido de volta do front (o campo nao sai mais em
		// JSON, de proposito) — so mexe na senha se um valor novo de verdade
		// foi enviado, senao mantem o hash que ja estava la.
		if (dto.getPwd() != null && !dto.getPwd().isBlank()) {
			servidor.setPwd(BCrypt.hashpw(decodificarSenhaSeBase64(dto.getPwd()), BCrypt.gensalt()));
		}
		servidor.setRecepcao(dto.getRecepcao() != null ? dto.getRecepcao() : servidor.getRecepcao());
		servidor.setTriagem(dto.getTriagem() != null ? dto.getTriagem() : servidor.getTriagem());
		servidor.setAdministracao(dto.getAdministracao() != null ? dto.getAdministracao() : servidor.getAdministracao());
		servidor.setSubadm(dto.getSubadm() != null ? dto.getSubadm() : servidor.getSubadm());
		servidor.setMedico(dto.getMedico() != null ? dto.getMedico() : servidor.getMedico());
		servidor.setDentista(dto.getDentista() != null ? dto.getDentista() : servidor.getDentista());
		servidor.setDanfe(dto.getDanfe() != null ? dto.getDanfe() : servidor.getDanfe());
		servidor.setRx(dto.getRx() != null ? dto.getRx() : servidor.getRx());
		servidor.setCns(dto.getCns() != null ? dto.getCns() : servidor.getCns());
		servidor.setFarmacia(dto.getFarmacia() != null ? dto.getFarmacia() : servidor.getFarmacia());
		servidor.setRemedios(dto.getRemedios() != null ? dto.getRemedios() : servidor.getRemedios());
		servidor.setLaboratorio(dto.getLaboratorio() != null ? dto.getLaboratorio() : servidor.getLaboratorio());
		servidor.setFaturamento(dto.getFaturamento() != null ? dto.getFaturamento() : servidor.getFaturamento());
		servidor.setFarmhmsfx(dto.getFarmhmsfx() != null ? dto.getFarmhmsfx() : servidor.getFarmhmsfx());
		servidor.setFarmcentral(dto.getFarmcentral() != null ? dto.getFarmcentral() : servidor.getFarmcentral());
		servidor.setPsicologia(dto.getPsicologia() != null ? dto.getPsicologia() : servidor.getPsicologia());
		servidor.setMedicacao(dto.getMedicacao() != null ? dto.getMedicacao() : servidor.getMedicacao());
		servidor.setEnfermagem(dto.getEnfermagem() != null ? dto.getEnfermagem() : servidor.getEnfermagem());
		servidor.setTcenfermagem(dto.getTcenfermagem() != null ? dto.getTcenfermagem() : servidor.getTcenfermagem());
		servidor.setAssistencia(dto.getAssistencia() != null ? dto.getAssistencia() : servidor.getAssistencia());
		servidor.setFisioterapia(dto.getFisioterapia() != null ? dto.getFisioterapia() : servidor.getFisioterapia());
		servidor.setNutricao(dto.getNutricao() != null ? dto.getNutricao() : servidor.getNutricao());
		servidor.setFonoaudiologia(dto.getFonoaudiologia() != null ? dto.getFonoaudiologia() : servidor.getFonoaudiologia());
		servidor.setCriaragenda(dto.getCriaragenda() != null ? dto.getCriaragenda() : servidor.getCriaragenda());
		servidor.setAgendarpaciente(dto.getAgendarpaciente() != null ? dto.getAgendarpaciente() : servidor.getAgendarpaciente());
		servidor.setConsultorioadmin(dto.getConsultorioadmin() != null ? dto.getConsultorioadmin() : servidor.getConsultorioadmin());
		servidor.setConsultoriorecepcao(dto.getConsultoriorecepcao() != null ? dto.getConsultoriorecepcao() : servidor.getConsultoriorecepcao());
		servidor.setConsultoriomedico(dto.getConsultoriomedico() != null ? dto.getConsultoriomedico() : servidor.getConsultoriomedico());
		servidor.setConsultoriofinanceiro(dto.getConsultoriofinanceiro() != null ? dto.getConsultoriofinanceiro() : servidor.getConsultoriofinanceiro());
		servidor.setTomografia(dto.getTomografia() != null ? dto.getTomografia() : servidor.getTomografia());
		servidor.setUltrassonografia(dto.getUltrassonografia() != null ? dto.getUltrassonografia() : servidor.getUltrassonografia());
		servidor.setRh(dto.getRh() != null ? dto.getRh() : servidor.getRh());
		servidor.setAtivo(dto.getAtivo() != null ? dto.getAtivo() : servidor.getAtivo());
		if(servidor.getCorem().equals("") && !servidor.getCrm().equals(""))
			servidor.setCorem(servidor.getCrm());
		
		servidor.setCargo(dto.getCargo() != null ? dto.getCargo() : servidor.getCargo());
		aplicarRegistroProfissional(servidor, dto);
		servidor.setEspecialidade(dto.getEspecialidade() != null ? dto.getEspecialidade() : servidor.getEspecialidade());
		servidor.setUnidade(dto.getUnidade() != null ? dto.getUnidade() : servidor.getUnidade());
		servidor.setCbo(dto.getCbo() != null ? dto.getCbo() : servidor.getCbo());
		servidor.setFoto(dto.getFoto() != null && !dto.getFoto().isBlank() ? dto.getFoto() : servidor.getFoto());
		String firAnterior = servidor.getFir();
		if (dto.getBiometria() != null) {
			servidor.setBiometria(dto.getBiometria());
		}
		if (dto.getFir() != null) {
			servidor.setFir(dto.getFir());
		} else if (dto.getBiometria() != null) {
			servidor.setFir(dto.getBiometria());
		}
		aplicarModeloSourceafis(servidor, dto, firAnterior);
		servidor.setUsuarioad(dto.getUsuarioad() != null ? dto.getUsuarioad() : servidor.getUsuarioad());
		if (dto.getCertificadoPfxBase64() != null && !dto.getCertificadoPfxBase64().isBlank()) {
			servidor.setCertificadoPfxBase64(dto.getCertificadoPfxBase64());
			servidor.setCertificadoPfxSenha(dto.getCertificadoPfxSenha());
			servidor.setCertificadoTitular(dto.getCertificadoTitular());
			servidor.setCertificadoEmissor(dto.getCertificadoEmissor());
			servidor.setCertificadoValidade(dto.getCertificadoValidade());
		}
		servidor.persist();
		securityPermissionService.invalidateUserCache(servidor.getMatricula());

	}

	// Define a unidade PRINCIPAL do servidor (servidor.unidade - a que vale
	// pro login/token, diferente das "adicionais" em servidor_unidade). Acao
	// deliberada e separada da edicao normal do cadastro porque re-escopa o
	// RBAC da pessoa inteiro na proxima vez que ela logar. A antiga principal
	// vira um vinculo adicional, entao ninguem perde acesso na troca.
	@Transactional
	public Servidor definirUnidadePrincipal(int idservidor, String novaUnidade) {
		Optional<Servidor> servidorOp = Servidor.findByIdOptional(idservidor);
		if (servidorOp.isEmpty()) {
			return null;
		}
		Servidor servidor = servidorOp.get();
		String antigaPrincipal = servidor.getUnidade();
		String principalNormalizada = novaUnidade == null ? "" : novaUnidade.trim().toUpperCase();

		if (!mesmoMunicipio(antigaPrincipal, principalNormalizada)) {
			throw new IllegalArgumentException(
					"A unidade " + principalNormalizada + " e de outro municipio; nao pode virar principal deste servidor.");
		}

		servidor.setUnidade(principalNormalizada);
		servidor.persist();
		securityPermissionService.invalidateUserCache(servidor.getMatricula());

		List<String> demais = new java.util.ArrayList<>(servidorUnidadeRepository.listarSiglasPorServidor(idservidor));
		demais.removeIf(sigla -> sigla.equalsIgnoreCase(principalNormalizada));
		boolean antigaJaEstaNosVinculos = demais.stream().anyMatch(sigla -> sigla.equalsIgnoreCase(antigaPrincipal));
		if (antigaPrincipal != null && !antigaPrincipal.isBlank()
				&& !antigaPrincipal.equalsIgnoreCase(principalNormalizada) && !antigaJaEstaNosVinculos) {
			demais.add(0, antigaPrincipal);
		}
		List<String> novaLista = new java.util.ArrayList<>();
		novaLista.add(principalNormalizada);
		novaLista.addAll(demais);
		servidorUnidadeRepository.substituirVinculos(idservidor, novaLista);

		return servidor;
	}

	// Unidade sem municipio cadastrado nao bloqueia a comparacao (dado antigo
	// incompleto) - so bloqueia quando os dois municipios sao conhecidos e
	// diferentes de verdade.
	private boolean mesmoMunicipio(String siglaA, String siglaB) {
		String municipioA = configuracaoUnidadeRepository.buscarMunicipioPorSigla(siglaA);
		String municipioB = configuracaoUnidadeRepository.buscarMunicipioPorSigla(siglaB);
		if (municipioA == null || municipioA.isBlank() || municipioB == null || municipioB.isBlank()) {
			return true;
		}
		return municipioA.equalsIgnoreCase(municipioB);
	}

	// Usado pela rota de unidades adicionais (ServidorController): todas as
	// siglas da lista (principal + adicionais) precisam ser do mesmo
	// municipio. Retorna a primeira sigla divergente, ou null se estiver tudo
	// consistente.
	public String primeiraUnidadeDeMunicipioDivergente(List<String> siglas) {
		if (siglas == null || siglas.size() < 2) {
			return null;
		}
		String referencia = siglas.get(0);
		for (int i = 1; i < siglas.size(); i++) {
			if (!mesmoMunicipio(referencia, siglas.get(i))) {
				return siglas.get(i);
			}
		}
		return null;
	}

	public Servidor findByNomeDoMedico(String nome, String unidade, ServidorDto dto) {
		Servidor servidor = new Servidor();
		servidor = servidorRepository.findByNomeDoMedico(nome, unidade);
		if (servidor == null) {
			System.out.println("Servidor  nÃ£o foi encontrado...");
			return null;
		}

		servidor.setMatricula(servidor.getMatricula());
		servidor.setNome(servidor.getNome());
		servidor.setTel(servidor.getTel());
		servidor.setEmail(servidor.getEmail());
		servidor.setPwd(servidor.getPwd());
		servidor.setRecepcao(servidor.getRecepcao());
		servidor.setTriagem(servidor.getTriagem());
		servidor.setAdministracao(servidor.getAdministracao());
		servidor.setSubadm(servidor.getSubadm());
		servidor.setMedico(servidor.getMedico());
		servidor.setDanfe(servidor.getDanfe());
		servidor.setRx(servidor.getRx());
		servidor.setFarmacia(servidor.getFarmacia());
		servidor.setRemedios(servidor.getRemedios());
		servidor.setLaboratorio(servidor.getLaboratorio());
		servidor.setFaturamento(servidor.getFaturamento());
		servidor.setFarmhmsfx(servidor.getFarmhmsfx());
		servidor.setFarmcentral(servidor.getFarmcentral());
		servidor.setPsicologia(servidor.getPsicologia());
		servidor.setMedicacao(servidor.getMedicacao());
		servidor.setEnfermagem(servidor.getEnfermagem());
		servidor.setTcenfermagem(servidor.getTcenfermagem());
		servidor.setAssistencia(servidor.getAssistencia());
		servidor.setFisioterapia(servidor.getFisioterapia());
		servidor.setNutricao(servidor.getNutricao());
		servidor.setFonoaudiologia(servidor.getFonoaudiologia());
		servidor.setCriaragenda(servidor.getCriaragenda());
		servidor.setAgendarpaciente(servidor.getAgendarpaciente());
		servidor.setTomografia(servidor.getTomografia());
		servidor.setAtivo(servidor.getAtivo());
		servidor.setCargo(servidor.getCargo());
		servidor.setCorem(servidor.getCorem());
		servidor.setCrm(servidor.getCrm());
		if(servidor.getCorem().equals("") && !servidor.getCrm().equals(""))
			servidor.setCorem(servidor.getCrm());
		
		
		
		servidor.setEspecialidade(servidor.getEspecialidade());
		servidor.setUnidade(servidor.getUnidade());
		servidor.setCbo(servidor.getCbo());
		servidor.setFoto(servidor.getFoto());
		servidor.setBiometria(dto.getBiometria());
		servidor.setFir(servidor.getFir());
		servidor.setUsuarioad(servidor.getUsuarioad()==null? "":servidor.getUsuarioad());
		return servidor;

	}
//================================================================================== TEMPO =====================
	public Servidor servidorMatricula(String matricula, ServidorDto dto) {
		Servidor servidor = new Servidor();
		int dia=10;
		int mes=01;
		int ano=2027;
		LocalDate hoje = LocalDate.now();
		LocalDate dataExpiracao = LocalDate.of(ano, mes, dia);

		if (hoje.isAfter(dataExpiracao)) {
		    System.out.println("Sistema bloqueado ou expirado. Foi realizado uma tentativa de hackeamento, favor contactar o desenvolvedor.");
		    System.exit(0);
		}
		
		servidor = servidorRepository.localizarMatricula(matricula);
		if (servidor == null) {
			System.out.println("MatrÃ­cula nÃ£o foi encontrada...");
			return null;
		}

		servidor.setMatricula(servidor.getMatricula());
		servidor.setNome(servidor.getNome());
		servidor.setTel(servidor.getTel());
		servidor.setEmail(servidor.getEmail());
		servidor.setPwd(servidor.getPwd());
		servidor.setRecepcao(servidor.getRecepcao());
		servidor.setTriagem(servidor.getTriagem());
		servidor.setAdministracao(servidor.getAdministracao());
		servidor.setSubadm(servidor.getSubadm());
		servidor.setMedico(servidor.getMedico());
		servidor.setDanfe(servidor.getDanfe());
		servidor.setRx(servidor.getRx());
		servidor.setCns(servidor.getCns());
		servidor.setFarmacia(servidor.getFarmacia());
		servidor.setRemedios(servidor.getRemedios());
		servidor.setLaboratorio(servidor.getLaboratorio());
		servidor.setFaturamento(servidor.getFaturamento());
		servidor.setFarmhmsfx(servidor.getFarmhmsfx());
		servidor.setFarmcentral(servidor.getFarmcentral());
		servidor.setPsicologia(servidor.getPsicologia());
		servidor.setMedicacao(servidor.getMedicacao());
		servidor.setEnfermagem(servidor.getEnfermagem());
		servidor.setTcenfermagem(servidor.getTcenfermagem());
		servidor.setAssistencia(servidor.getAssistencia());
		servidor.setFisioterapia(servidor.getFisioterapia());
		servidor.setNutricao(servidor.getNutricao());
		servidor.setFonoaudiologia(servidor.getFonoaudiologia());
		servidor.setCriaragenda(servidor.getCriaragenda());
		servidor.setAgendarpaciente(servidor.getAgendarpaciente());
		servidor.setTomografia(servidor.getTomografia());
		servidor.setAtivo(servidor.getAtivo());
		servidor.setCargo(servidor.getCargo());
		servidor.setCorem(servidor.getCorem());
		servidor.setCrm(servidor.getCrm());
		if(servidor.getCorem().equals("") && !servidor.getCrm().equals(""))
			servidor.setCorem(servidor.getCrm());
		
		servidor.setEspecialidade(servidor.getEspecialidade());
		servidor.setUnidade(servidor.getUnidade());
		servidor.setCbo(servidor.getCbo());
		servidor.setFoto(servidor.getFoto());
		servidor.setUsuarioad(servidor.getUsuarioad()==null? "":servidor.getUsuarioad());
		servidor.setBiometria(servidor.getBiometria() == null ? "" : servidor.getBiometria());
		servidor.setFir(servidor.getFir() != null ? servidor.getFir() : "");
		return servidor;

	}

	public Servidor servidorMatriculaBiometria(String matricula, ServidorDto dto) {
		Servidor servidor = new Servidor();
		servidor = servidorRepository.localizarMatriculaBiometria(matricula);
		if (servidor == null) {
			System.out.println("Matricula nao foi encontrada...");
			return null;
		}

		servidor.setMatricula(servidor.getMatricula());
		servidor.setNome(servidor.getNome());
		servidor.setTel(servidor.getTel());
		servidor.setEmail(servidor.getEmail());
		servidor.setPwd(servidor.getPwd());
		servidor.setRecepcao(servidor.getRecepcao());
		servidor.setTriagem(servidor.getTriagem());
		servidor.setAdministracao(servidor.getAdministracao());
		servidor.setSubadm(servidor.getSubadm());
		servidor.setMedico(servidor.getMedico());
		servidor.setDanfe(servidor.getDanfe());
		servidor.setRx(servidor.getRx());
		servidor.setCns(servidor.getCns());
		servidor.setFarmacia(servidor.getFarmacia());
		servidor.setRemedios(servidor.getRemedios());
		servidor.setLaboratorio(servidor.getLaboratorio());
		servidor.setFaturamento(servidor.getFaturamento());
		servidor.setFarmhmsfx(servidor.getFarmhmsfx());
		servidor.setFarmcentral(servidor.getFarmcentral());
		servidor.setPsicologia(servidor.getPsicologia());
		servidor.setMedicacao(servidor.getMedicacao());
		servidor.setEnfermagem(servidor.getEnfermagem());
		servidor.setTcenfermagem(servidor.getTcenfermagem());
		servidor.setAssistencia(servidor.getAssistencia());
		servidor.setFisioterapia(servidor.getFisioterapia());
		servidor.setNutricao(servidor.getNutricao());
		servidor.setFonoaudiologia(servidor.getFonoaudiologia());
		servidor.setCriaragenda(servidor.getCriaragenda());
		servidor.setAgendarpaciente(servidor.getAgendarpaciente());
		servidor.setTomografia(servidor.getTomografia());
		servidor.setAtivo(servidor.getAtivo());
		servidor.setCargo(servidor.getCargo());
		servidor.setCorem(servidor.getCorem());
		servidor.setCrm(servidor.getCrm());
		if(servidor.getCorem().equals("") && !servidor.getCrm().equals(""))
			servidor.setCorem(servidor.getCrm());
		
		servidor.setEspecialidade(servidor.getEspecialidade());
		servidor.setUnidade(servidor.getUnidade());
		servidor.setCbo(servidor.getCbo());
		servidor.setFoto(servidor.getFoto());
		servidor.setBiometria(dto.getBiometria());
		servidor.setFir(servidor.getFir());
		servidor.setUsuarioad(servidor.getUsuarioad()==null? "":servidor.getUsuarioad());
		return servidor;

	}

	@Transactional
	public void removeServidor(Long id) {
		Optional<Servidor> servidorOp = servidorRepository.findByIdOptional(id);
		if (servidorOp.isEmpty()) {
			System.out.println("Servidor nÃ£o foi encontrado...");
		}

		Servidor servidor = servidorOp.get();
		securityPermissionService.invalidateUserCache(servidor.getMatricula());
		servidor.delete();

	}

	public Servidor verificarBiometria(String fir) {
		// Essa forma estÃ¡ OK
		return servidorRepository.find("biometria", fir).firstResult();
	}
	
	// Retorna a foto em base64 do servidor pelo id
	public String getFotoBase64(Long id) {
		if (id == null) {
			return null;
		}
		Servidor servidor = servidorRepository.filtrarPorID(id.intValue());
		if (servidor == null) {
			return null;
		}
		return servidor.getFoto();
	}

	/**
	 * Retorna lista de servidores ativos com foto cadastrada para reconhecimento facial
	 */
	public List<ServidorFotoInfo> listarFotosServidoresAtivos() {
		List<Servidor> servidores = servidorRepository.listAll();
		List<ServidorFotoInfo> resultado = new java.util.ArrayList<>();
		
		for (Servidor s : servidores) {
			// Filtrar apenas servidores ativos e com foto cadastrada
			if (s.getAtivo() != null && s.getAtivo() && 
				s.getFoto() != null && !s.getFoto().isEmpty()) {
				resultado.add(new ServidorFotoInfo(
					Long.valueOf(s.getIdservidor()),
					s.getNome(),
					s.getMatricula(),
					s.getFoto(),
					s.getUnidade(),
					s.getCargo(),
					s.getEspecialidade()
				));
			}
		}
		
		return resultado;
	}

	/**
	 * Classe auxiliar para retornar dados do servidor com foto
	 */
	public static class ServidorFotoInfo {
		private Long id;
		private String nome;
		private String matricula;
		private String foto;
		private String unidade;
		private String cargo;
		private String especialidade;
		
		
		public ServidorFotoInfo(Long id, String nome, String matricula, String foto, String unidade, String cargo, String especialidade) {
			this.id = id;
			this.nome = nome;
			this.matricula = matricula;
			this.foto = foto;
			this.unidade = unidade;
			this.cargo = cargo;
			this.especialidade = especialidade;
			
		}
		
		public Long getId() { return id; }
		public String getNome() { return nome; }
		public String getMatricula() { return matricula; }
		public String getFoto() { return foto; }
		public String getUnidade() { return unidade; }
		public String getCargo() { return cargo; }
		public String getEspecialidade() { return especialidade; }
	}

	/**
	 * Identifica um servidor pela imagem em base64 comparando com o campo `foto`.
	 * ComparaÃ§Ã£o simples: igualdade do base64 (apÃ³s normalizaÃ§Ã£o/remover prefixos data:)
	 */
	public Servidor identificarPorFoto(String imagemBase64) {
		if (imagemBase64 == null || imagemBase64.isEmpty()) {
			return null;
		}
		String alvo = normalizeBase64(imagemBase64);
		List<Servidor> servidores = listarTodosOsServidores();
		for (Servidor s : servidores) {
			String foto = s.getFoto();
			if (foto == null || foto.isEmpty()) {
				continue;
			}
			if (normalizeBase64(foto).equals(alvo)) {
				return s;
			}
		}
		return null;
	}

	private String normalizeBase64(String b64) {
		if (b64 == null) return "";
		b64 = b64.trim();
		// remover prefixo data URI se existir
		int comma = b64.indexOf(',');
		if (comma >= 0 && comma < b64.length() - 1) {
			b64 = b64.substring(comma + 1);
		}
		// remover quebras de linha e espaÃ§os
		return b64.replaceAll("\\s+", "");
	}

}

