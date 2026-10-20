package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.Servidor2Dto;
import br.com.iv.entity.Servidor2;
import br.com.iv.entity.Setores;
import br.com.iv.repository.Servidor2Repository;
import br.com.iv.security.SecurityPermissionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class Servidor2Service implements Serializable {
	@Inject
	ServidorService servidorService;
	public Servidor2 buscarPorMatricula(String matricula) {
		return servidor2Repository.localizarMatricula(matricula);
	}

	private static final long serialVersionUID = 1L;

	@Inject
	Servidor2Repository servidor2Repository;

	@Inject
	SecurityPermissionService securityPermissionService;

	// Listando todos os setores
	public List<Servidor2> listarTodosOsServidor2es() {
		return servidor2Repository.listAll();
	}

	// Filtar por setor e unidade
	@Transactional
	public List<Servidor2> filtarPorServidor2(String Servidor2, String unidade) {
		// Setores setores = new Setores();
		List<Servidor2> Servidor2Op = servidor2Repository.filtarPorServidor2(Servidor2, unidade);
		if (Servidor2Op.isEmpty()) {
			System.out.println("Servidor2 nÃ£o encontrado...");
		}
		return Servidor2Op;
	}

	@Transactional
	public void alterarSenha(String matricula) {
		servidor2Repository.alterarSenha(matricula);
	}

	@Transactional
	public void alterarStatus(int id, boolean status) {
		servidor2Repository.alterarStatus(id, status);
	}

	@Transactional
	public Servidor2 Servidor2Id(int id) {
		return servidor2Repository.filtrarPorID(id);
	}

	@Transactional
	public List<Servidor2> filtarPorUnidadeServidor2(String unidade, Servidor2Dto dto) {
		List<Servidor2> Servidor2Op = servidor2Repository.filtarPorUnidadeDoServidor2(unidade);
		if (Servidor2Op.isEmpty()) {
			System.out.println("NÃ£o hÃ¡ Servidor2es cadastrados nessa unidade...");
		}
		return Servidor2Op;
	}

	@Transactional
	public List<Servidor2> filtarPorUnidadeMedico(String unidade, Servidor2Dto dto) {

		// Setores setores = new Setores();
		List<Servidor2> Servidor2Op = servidor2Repository.filtarPorUnidadeDoServidor2Medico(unidade);
		if (Servidor2Op.isEmpty()) {
			System.out.println("NÃ£o hÃ¡ Servidor2es cadastrados nessa unidade...");
		}
		return Servidor2Op;
	}

	// Salvar os setores
	@Transactional
	public Servidor2 salvarServidor2(Servidor2Dto dto) {
		// Uma segunda linha para a mesma matricula faz o login ler permissoes de um
		// registro zerado. Cadastro novo so vale se a matricula ainda nao existir.
		if (dto.getMatricula() != null && !dto.getMatricula().isBlank()) {
			Servidor2 existente = servidor2Repository.localizarMatricula(dto.getMatricula().trim());
			if (existente != null) {
				throw new IllegalStateException("Matricula " + dto.getMatricula() + " ja possui permissoes administrativas cadastradas.");
			}
		}

		Servidor2 Servidor2 = new Servidor2();
		Servidor2.setMatricula(dto.getMatricula());
		Servidor2.setNome(dto.getNome());
		Servidor2.setTel(dto.getTel());
		Servidor2.setEmail(dto.getEmail());
		Servidor2.setUnidade(dto.getUnidade());
		Servidor2.setCns(dto.getCns());
		// Garantir valores nÃ£o-nulos para colunas definidas como not null no banco
		Servidor2.setSuperusuario(dto.getSuperusuario() != null ? dto.getSuperusuario() : false);
		Servidor2.setCadsalas(dto.getCadsalas() != null ? dto.getCadsalas() : false);
		Servidor2.setCadsetores(dto.getCadsetores() != null ? dto.getCadsetores() : false);
		Servidor2.setCadunidades(dto.getCadunidades() != null ? dto.getCadunidades() : false);
		Servidor2.setCadusuarios(dto.getCadusuarios() != null ? dto.getCadusuarios() : false);
		Servidor2.setCaddrives(dto.getCaddrives() != null ? dto.getCaddrives() : false);
		Servidor2.setAuditoriaclinica(dto.getAuditoriaclinica() != null ? dto.getAuditoriaclinica() : false);
		Servidor2.setListausuarios(dto.getListausuarios() != null ? dto.getListausuarios() : false);
		Servidor2.setMapapacientes(dto.getMapapacientes() != null ? dto.getMapapacientes() : false);
		Servidor2.setResumopaciente(dto.getResumopaciente() != null ? dto.getResumopaciente() : false);
		Servidor2.setAguardandoatendimento(dto.getAguardandoatendimento() != null ? dto.getAguardandoatendimento() : false);
		Servidor2.setAtendimentoperiodo(dto.getAtendimentoperiodo() != null ? dto.getAtendimentoperiodo() : false);
		Servidor2.setBiometria(dto.getBiometria() != null ? dto.getBiometria() : false);
		Servidor2.setComunicacaoespecialistas(dto.getComunicacaoespecialistas() != null ? dto.getComunicacaoespecialistas() : false);
		Servidor2.setCorrecaobam(dto.getCorrecaobam() != null ? dto.getCorrecaobam() : false);
		Servidor2.setEnfermariaseleitos(dto.getEnfermariaseleitos() != null ? dto.getEnfermariaseleitos() : false);
		Servidor2.setImprimirbam(dto.getImprimirbam() != null ? dto.getImprimirbam() : false);
		Servidor2.setLimparpainel(dto.getLimparpainel() != null ? dto.getLimparpainel() : false);
		Servidor2.setPacientesematendimento(dto.getPacientesematendimento() != null ? dto.getPacientesematendimento() : false);
		Servidor2.setAvaliacaopacienteia_adm(dto.getAvaliacaopacienteia_adm() != null ? dto.getAvaliacaopacienteia_adm() : false);
		Servidor2.setPendenciaassinatura(dto.getPendenciaassinatura() != null ? dto.getPendenciaassinatura() : false);
		Servidor2.setHistoricocompleto_adm(dto.getHistoricocompleto_adm() != null ? dto.getHistoricocompleto_adm() : false);
		Servidor2.setProntuariocompleto(dto.getProntuariocompleto() != null ? dto.getProntuariocompleto() : false);
		Servidor2.setExportardadoslgpd(dto.getExportardadoslgpd() != null ? dto.getExportardadoslgpd() : false);
		Servidor2.setComoestaopaciente(dto.getComoestaopaciente() != null ? dto.getComoestaopaciente() : false);
		Servidor2.setVerificarassinatura(dto.getVerificarassinatura() != null ? dto.getVerificarassinatura() : false);
		Servidor2.setRagprontuario(dto.getRagprontuario() != null ? dto.getRagprontuario() : false);
		Servidor2.setResumoclinicoia(dto.getResumoclinicoia() != null ? dto.getResumoclinicoia() : false);
		Servidor2.setPersonalizacaovisual(dto.getPersonalizacaovisual() != null ? dto.getPersonalizacaovisual() : false);
		Servidor2.setPacs(dto.getPacs() != null ? dto.getPacs() : false);
		Servidor2.setPcentrocirurgico(dto.getPcentrocirurgico() != null ? dto.getPcentrocirurgico() : false);
		Servidor2.setPcirurgiaagendar(dto.getPcirurgiaagendar() != null ? dto.getPcirurgiaagendar() : false);
		Servidor2.setPcirurgiaexecutar(dto.getPcirurgiaexecutar() != null ? dto.getPcirurgiaexecutar() : false);
		Servidor2.setPcirurgiadescrever(dto.getPcirurgiadescrever() != null ? dto.getPcirurgiadescrever() : false);
		Servidor2.setPcirurgiaanestesia(dto.getPcirurgiaanestesia() != null ? dto.getPcirurgiaanestesia() : false);
		Servidor2.setPcirurgiasuspender(dto.getPcirurgiasuspender() != null ? dto.getPcirurgiasuspender() : false);
		Servidor2.setPcirurgiaopme(dto.getPcirurgiaopme() != null ? dto.getPcirurgiaopme() : false);
		Servidor2.setPcirurgiasalas(dto.getPcirurgiasalas() != null ? dto.getPcirurgiasalas() : false);
		Servidor2.setPcirurgialistaespera(dto.getPcirurgialistaespera() != null ? dto.getPcirurgialistaespera() : false);
		Servidor2.setPapoioassistencial(dto.getPapoioassistencial() != null ? dto.getPapoioassistencial() : false);
		Servidor2.setPcme(dto.getPcme() != null ? dto.getPcme() : false);
		Servidor2.setPhemoterapia(dto.getPhemoterapia() != null ? dto.getPhemoterapia() : false);
		Servidor2.setPsnd(dto.getPsnd() != null ? dto.getPsnd() : false);
		Servidor2.setPrnds(dto.getPrnds() != null ? dto.getPrnds() : false);
		Servidor2.setPnsp(dto.getPnsp() != null ? dto.getPnsp() : false);
		Servidor2.setPnspgestao(dto.getPnspgestao() != null ? dto.getPnspgestao() : false);
		Servidor2.setPnspclassificar(dto.getPnspclassificar() != null ? dto.getPnspclassificar() : false);
		Servidor2.setPnspbloqueado(dto.getPnspbloqueado() != null ? dto.getPnspbloqueado() : false);
		Servidor2.setPfhirsumarioalta(dto.getPfhirsumarioalta() != null ? dto.getPfhirsumarioalta() : false);
		Servidor2.setLogs(dto.getLogs() != null ? dto.getLogs() : false);
		Servidor2.setVigilanciaepidemiologica(dto.getVigilanciaepidemiologica() != null ? dto.getVigilanciaepidemiologica() : false);
		Servidor2.setConfiguracaovigilanciaepidemiologica(dto.getConfiguracaovigilanciaepidemiologica() != null ? dto.getConfiguracaovigilanciaepidemiologica() : false);
		Servidor2.setProducaomedica(dto.getProducaomedica() != null ? dto.getProducaomedica() : false);
		Servidor2.setIntegracaosus(dto.getIntegracaosus() != null ? dto.getIntegracaosus() : false);
		Servidor2.setMigracaodados(dto.getMigracaodados() != null ? dto.getMigracaodados() : false);

		// Define as permissÃµes NIR
		Servidor2.setCadleitos(dto.getCadleitos() != null ? dto.getCadleitos() : false);
		Servidor2.setCadsetor(dto.getCadsetor() != null ? dto.getCadsetor() : false);
		Servidor2.setRestaurarpaciente(dto.getRestaurarpaciente() != null ? dto.getRestaurarpaciente() : false);
		Servidor2.setBiexecutivo(dto.getBiexecutivo() != null ? dto.getBiexecutivo() : false);
		Servidor2.setNirrestaurarinternacao(dto.getNirrestaurarinternacao() != null ? dto.getNirrestaurarinternacao() : false);
		Servidor2.setGed(dto.getGed() != null ? dto.getGed() : false);
		Servidor2.setGedremover(dto.getGedremover() != null ? dto.getGedremover() : false);
		Servidor2.setGedconsulta(dto.getGedconsulta() != null ? dto.getGedconsulta() : false);
		Servidor2.setCautela(dto.getCautela() != null ? dto.getCautela() : false);
		Servidor2.setFarmaciaprodutos(dto.getFarmaciaprodutos() != null ? dto.getFarmaciaprodutos() : false);
		Servidor2.setFarmaciaestoque(dto.getFarmaciaestoque() != null ? dto.getFarmaciaestoque() : false);
		Servidor2.setFarmaciadispensacao(dto.getFarmaciadispensacao() != null ? dto.getFarmaciadispensacao() : false);
		Servidor2.setFarmaciapermutas(dto.getFarmaciapermutas() != null ? dto.getFarmaciapermutas() : false);
		Servidor2.setFarmaciaoperacoes(dto.getFarmaciaoperacoes() != null ? dto.getFarmaciaoperacoes() : false);
		Servidor2.setFarmaciaavaliacao(dto.getFarmaciaavaliacao() != null ? dto.getFarmaciaavaliacao() : false);
		Servidor2.setFarmaciarelatorios(dto.getFarmaciarelatorios() != null ? dto.getFarmaciarelatorios() : false);
		Servidor2.setRegrasdose(dto.getRegrasdose() != null ? dto.getRegrasdose() : false);
		Servidor2.setNirlaudointernacao(dto.getNirlaudointernacao() != null ? dto.getNirlaudointernacao() : false);
		Servidor2.setNirenfermariaseleitos(dto.getNirenfermariaseleitos() != null ? dto.getNirenfermariaseleitos() : false);
		Servidor2.setNirhistoricocompleto(dto.getNirhistoricocompleto() != null ? dto.getNirhistoricocompleto() : false);
		Servidor2.setNir(dto.getNir() != null ? dto.getNir() : false);
		Servidor2.setPainelnir(dto.getPainelnir() != null ? dto.getPainelnir() : false);
		Servidor2.setLocalcenso(dto.getLocalcenso() != null ? dto.getLocalcenso() : false);
		Servidor2.setPesquisanir(dto.getPesquisanir() != null ? dto.getPesquisanir() : false);
		Servidor2.setNirrelatorio(dto.getNirrelatorio() != null ? dto.getNirrelatorio() : false);
		Servidor2.setSetorcenso(dto.getSetorcenso() != null ? dto.getSetorcenso() : false);
		Servidor2.setInternadosvisitaimp(dto.getInternadosvisitaimp() != null ? dto.getInternadosvisitaimp() : false);
		
		// Define as permissÃµes Enfermagem
			Servidor2.setCcih(dto.getCcih() != null ? dto.getCcih() : false);
			Servidor2.setBalancohidrico(dto.getBalancohidrico() != null ? dto.getBalancohidrico() : false);
			// Garantir que campos booleanos nÃ£o fiquem nulos (o banco exige NOT NULL)
			Servidor2.setDriveenfermagem(dto.getDriveenfermagem() != null ? dto.getDriveenfermagem() : false);
			Servidor2.setEscalaenfermagem(dto.getEscalaenfermagem() != null ? dto.getEscalaenfermagem() : false);
			Servidor2.setEvolucaomultidisciplinar(dto.getEvolucaomultidisciplinar() != null ? dto.getEvolucaomultidisciplinar() : false);
			Servidor2.setPedidosexame(dto.getPedidosexame() != null ? dto.getPedidosexame() : false);
			Servidor2.setMinhasassinaturas(dto.getMinhasassinaturas() != null ? dto.getMinhasassinaturas() : false);
			Servidor2.setAvaliacaopacienteia(dto.getAvaliacaopacienteia() != null ? dto.getAvaliacaopacienteia() : false);
			Servidor2.setBulario(dto.getBulario() != null ? dto.getBulario() : false);
			Servidor2.setBulariomedico(dto.getBulariomedico() != null ? dto.getBulariomedico() : false);
			Servidor2.setCiclomedicacao(dto.getCiclomedicacao() != null ? dto.getCiclomedicacao() : false);
			Servidor2.setHistoricocompleto(dto.getHistoricocompleto() != null ? dto.getHistoricocompleto() : false);
			Servidor2.setAnotacoesenfermagem(dto.getAnotacoesenfermagem() != null ? dto.getAnotacoesenfermagem() : false);
			Servidor2.setSaeenfermagem(dto.getSaeenfermagem() != null ? dto.getSaeenfermagem() : false);
			Servidor2.setClassificacaoghbam(dto.getClassificacaoghbam() != null ? dto.getClassificacaoghbam() : false);
			Servidor2.setClassificacaoghbam_revisar(dto.getClassificacaoghbam_revisar() != null ? dto.getClassificacaoghbam_revisar() : false);
			Servidor2.setClassificacaoghbam_aprovar(dto.getClassificacaoghbam_aprovar() != null ? dto.getClassificacaoghbam_aprovar() : false);
			Servidor2.setClassificacaoghbam_admin(dto.getClassificacaoghbam_admin() != null ? dto.getClassificacaoghbam_admin() : false);
			Servidor2.setClassificacaoghbam_auditar(dto.getClassificacaoghbam_auditar() != null ? dto.getClassificacaoghbam_auditar() : false);
			Servidor2.setControleassistencial(dto.getControleassistencial() != null ? dto.getControleassistencial() : false);
			Servidor2.setSinan(dto.getSinan() != null ? dto.getSinan() : false);
			Servidor2.setDocumentosassistenciais(dto.getDocumentosassistenciais() != null ? dto.getDocumentosassistenciais() : false);
			Servidor2.setPainelenfermagem(dto.getPainelenfermagem() != null ? dto.getPainelenfermagem() : false);
			Servidor2.setPainelspa(dto.getPainelspa() != null ? dto.getPainelspa() : false);
			Servidor2.setPainelmaternidade(dto.getPainelmaternidade() != null ? dto.getPainelmaternidade() : false);
			Servidor2.setPainelpediatria(dto.getPainelpediatria() != null ? dto.getPainelpediatria() : false);
			Servidor2.setUltrassonografia(dto.getUltrassonografia() != null ? dto.getUltrassonografia() : false);
			Servidor2.setModeloevolucaoenf(dto.getModeloevolucaoenf() != null ? dto.getModeloevolucaoenf() : false);
			Servidor2.setModeloprescricaoenf(dto.getModeloprescricaoenf() != null ? dto.getModeloprescricaoenf() : false);
			Servidor2.setResumopacientes(dto.getResumopacientes() != null ? dto.getResumopacientes() : false);
			Servidor2.setConsultorioadmin(dto.getConsultorioadmin() != null ? dto.getConsultorioadmin() : false);
			Servidor2.setConsultoriorecepcao(dto.getConsultoriorecepcao() != null ? dto.getConsultoriorecepcao() : false);
			Servidor2.setConsultoriomedico(dto.getConsultoriomedico() != null ? dto.getConsultoriomedico() : false);
			Servidor2.setConsultoriofinanceiro(dto.getConsultoriofinanceiro() != null ? dto.getConsultoriofinanceiro() : false);
		Servidor2.setChatprofissionais(dto.getChatprofissionais() != null ? dto.getChatprofissionais() : false);
		Servidor2.setMonitoramento(dto.getMonitoramento() != null ? dto.getMonitoramento() : false);
		Servidor2.setControleplantoes(dto.getControleplantoes() != null ? dto.getControleplantoes() : false);
		Servidor2.setProdutividaderh(dto.getProdutividaderh() != null ? dto.getProdutividaderh() : false);
		Servidor2.setTreinamentocertificacao(dto.getTreinamentocertificacao() != null ? dto.getTreinamentocertificacao() : false);
		Servidor2.setGerenciarcertificados(dto.getGerenciarcertificados() != null ? dto.getGerenciarcertificados() : false);
		Servidor2.setFaturamentosuslancar(dto.getFaturamentosuslancar() != null ? dto.getFaturamentosuslancar() : false);
		Servidor2.setFaturamentosuscorrigir(dto.getFaturamentosuscorrigir() != null ? dto.getFaturamentosuscorrigir() : false);
		Servidor2.setFaturamentosusauditar(dto.getFaturamentosusauditar() != null ? dto.getFaturamentosusauditar() : false);
		Servidor2.setFaturamentosusfechar(dto.getFaturamentosusfechar() != null ? dto.getFaturamentosusfechar() : false);
		Servidor2.setFaturamentosusreabrir(dto.getFaturamentosusreabrir() != null ? dto.getFaturamentosusreabrir() : false);
		Servidor2.setSigtapimportar(dto.getSigtapimportar() != null ? dto.getSigtapimportar() : false);
		Servidor2.setBpaexportar(dto.getBpaexportar() != null ? dto.getBpaexportar() : false);
		Servidor2.setApacautorizar(dto.getApacautorizar() != null ? dto.getApacautorizar() : false);
		Servidor2.setAihauditar(dto.getAihauditar() != null ? dto.getAihauditar() : false);
		Servidor2.setFaturamentosusimportarretorno(dto.getFaturamentosusimportarretorno() != null ? dto.getFaturamentosusimportarretorno() : false);
		Servidor2.setAps(dto.getAps() != null ? dto.getAps() : false);
		Servidor2.setApsacs(dto.getApsacs() != null ? dto.getApsacs() : false);
		Servidor2.setApsvacina(dto.getApsvacina() != null ? dto.getApsvacina() : false);
		Servidor2.setApsgestao(dto.getApsgestao() != null ? dto.getApsgestao() : false);
		Servidor2.setGpecatendimentos(Boolean.TRUE.equals(dto.getGpecatendimentos()));
		Servidor2.setGpeccidadaos(Boolean.TRUE.equals(dto.getGpeccidadaos()));
		Servidor2.setGpecagenda(Boolean.TRUE.equals(dto.getGpecagenda()));
		Servidor2.setGpecvacinacao(Boolean.TRUE.equals(dto.getGpecvacinacao()));
		Servidor2.setGpecterritorio(Boolean.TRUE.equals(dto.getGpecterritorio()));
		Servidor2.setGpecregistros(Boolean.TRUE.equals(dto.getGpecregistros()));
		Servidor2.setGpecacompanhamento(Boolean.TRUE.equals(dto.getGpecacompanhamento()));
		Servidor2.setGpecrelatorios(Boolean.TRUE.equals(dto.getGpecrelatorios()));
		Servidor2.setGpectransmissao(Boolean.TRUE.equals(dto.getGpectransmissao()));
		Servidor2.setGpecconfiguracao(Boolean.TRUE.equals(dto.getGpecconfiguracao()));
		Servidor2.setGpecappacs(Boolean.TRUE.equals(dto.getGpecappacs()));
		aplicarEstadoSuperusuario(Servidor2, false);
		Servidor2.persist();
		securityPermissionService.invalidateUserCache(Servidor2.getMatricula());
		return Servidor2;
	}

	// Alterar
	@Transactional
	public void atualizarServidor2(int id, Servidor2Dto dto) {
		Optional<Servidor2> Servidor2Op = Servidor2.findByIdOptional(id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
		Servidor2 Servidor2 = Servidor2Op
				.orElseThrow(() -> new NullPointerException("O Servidor2 nÃ£o foi encontrado..."));
		boolean eraSuperusuario = Boolean.TRUE.equals(Servidor2.getSuperusuario());

		ServidorMatriculaService.validarMatriculaCadastro(Servidor2.getMatricula(), dto.getMatricula());
		Servidor2.setNome(dto.getNome());
		Servidor2.setTel(dto.getTel());
		Servidor2.setEmail(dto.getEmail());
		Servidor2.setUnidade(dto.getUnidade() != null ? dto.getUnidade() : Servidor2.getUnidade());
		Servidor2.setCns(dto.getCns() != null ? dto.getCns() : Servidor2.getCns());
		// Garantir que booleans nÃ£o sejam nulos ao atualizar
		Servidor2.setSuperusuario(dto.getSuperusuario() != null ? dto.getSuperusuario() : Servidor2.getSuperusuario());
		Servidor2.setCadsalas(dto.getCadsalas() != null ? dto.getCadsalas() : Servidor2.getCadsalas());
		Servidor2.setCadsetores(dto.getCadsetores() != null ? dto.getCadsetores() : Servidor2.getCadsetores());
		Servidor2.setCadunidades(dto.getCadunidades() != null ? dto.getCadunidades() : Servidor2.getCadunidades());
		Servidor2.setCadusuarios(dto.getCadusuarios() != null ? dto.getCadusuarios() : Servidor2.getCadusuarios());
		Servidor2.setCaddrives(dto.getCaddrives() != null ? dto.getCaddrives() : Servidor2.getCaddrives());
		Servidor2.setAuditoriaclinica(dto.getAuditoriaclinica() != null ? dto.getAuditoriaclinica() : Servidor2.getAuditoriaclinica());
		Servidor2.setListausuarios(dto.getListausuarios() != null ? dto.getListausuarios() : Servidor2.getListausuarios());
		Servidor2.setMapapacientes(dto.getMapapacientes() != null ? dto.getMapapacientes() : Servidor2.getMapapacientes());
		Servidor2.setResumopaciente(dto.getResumopaciente() != null ? dto.getResumopaciente() : Servidor2.getResumopaciente());
		Servidor2.setAguardandoatendimento(dto.getAguardandoatendimento() != null ? dto.getAguardandoatendimento() : Servidor2.getAguardandoatendimento());
		Servidor2.setAtendimentoperiodo(dto.getAtendimentoperiodo() != null ? dto.getAtendimentoperiodo() : Servidor2.getAtendimentoperiodo());
		Servidor2.setBiometria(dto.getBiometria() != null ? dto.getBiometria() : Servidor2.getBiometria());
		Servidor2.setComunicacaoespecialistas(dto.getComunicacaoespecialistas() != null ? dto.getComunicacaoespecialistas() : Servidor2.getComunicacaoespecialistas());
		Servidor2.setCorrecaobam(dto.getCorrecaobam() != null ? dto.getCorrecaobam() : Servidor2.getCorrecaobam());
		Servidor2.setEnfermariaseleitos(dto.getEnfermariaseleitos() != null ? dto.getEnfermariaseleitos() : Servidor2.getEnfermariaseleitos());
		Servidor2.setImprimirbam(dto.getImprimirbam() != null ? dto.getImprimirbam() : Servidor2.getImprimirbam());
		Servidor2.setLimparpainel(dto.getLimparpainel() != null ? dto.getLimparpainel() : Servidor2.getLimparpainel());
		Servidor2.setPacientesematendimento(dto.getPacientesematendimento() != null ? dto.getPacientesematendimento() : Servidor2.getPacientesematendimento());
		Servidor2.setAvaliacaopacienteia_adm(dto.getAvaliacaopacienteia_adm() != null ? dto.getAvaliacaopacienteia_adm() : Servidor2.getAvaliacaopacienteia_adm());
		Servidor2.setPendenciaassinatura(dto.getPendenciaassinatura() != null ? dto.getPendenciaassinatura() : Servidor2.getPendenciaassinatura());
		Servidor2.setHistoricocompleto_adm(dto.getHistoricocompleto_adm() != null ? dto.getHistoricocompleto_adm() : Servidor2.getHistoricocompleto_adm());
		Servidor2.setProntuariocompleto(dto.getProntuariocompleto() != null ? dto.getProntuariocompleto() : Servidor2.getProntuariocompleto());
		Servidor2.setExportardadoslgpd(dto.getExportardadoslgpd() != null ? dto.getExportardadoslgpd() : Servidor2.getExportardadoslgpd());
		Servidor2.setComoestaopaciente(dto.getComoestaopaciente() != null ? dto.getComoestaopaciente() : Servidor2.getComoestaopaciente());
		Servidor2.setVerificarassinatura(dto.getVerificarassinatura() != null ? dto.getVerificarassinatura() : Servidor2.getVerificarassinatura());
		Servidor2.setRagprontuario(dto.getRagprontuario() != null ? dto.getRagprontuario() : Servidor2.getRagprontuario());
		Servidor2.setResumoclinicoia(dto.getResumoclinicoia() != null ? dto.getResumoclinicoia() : Servidor2.getResumoclinicoia());
		Servidor2.setPersonalizacaovisual(dto.getPersonalizacaovisual() != null ? dto.getPersonalizacaovisual() : Servidor2.getPersonalizacaovisual());
		Servidor2.setPacs(dto.getPacs() != null ? dto.getPacs() : Servidor2.getPacs());
		Servidor2.setPcentrocirurgico(dto.getPcentrocirurgico() != null ? dto.getPcentrocirurgico() : Servidor2.getPcentrocirurgico());
		Servidor2.setPcirurgiaagendar(dto.getPcirurgiaagendar() != null ? dto.getPcirurgiaagendar() : Servidor2.getPcirurgiaagendar());
		Servidor2.setPcirurgiaexecutar(dto.getPcirurgiaexecutar() != null ? dto.getPcirurgiaexecutar() : Servidor2.getPcirurgiaexecutar());
		Servidor2.setPcirurgiadescrever(dto.getPcirurgiadescrever() != null ? dto.getPcirurgiadescrever() : Servidor2.getPcirurgiadescrever());
		Servidor2.setPcirurgiaanestesia(dto.getPcirurgiaanestesia() != null ? dto.getPcirurgiaanestesia() : Servidor2.getPcirurgiaanestesia());
		Servidor2.setPcirurgiasuspender(dto.getPcirurgiasuspender() != null ? dto.getPcirurgiasuspender() : Servidor2.getPcirurgiasuspender());
		Servidor2.setPcirurgiaopme(dto.getPcirurgiaopme() != null ? dto.getPcirurgiaopme() : Servidor2.getPcirurgiaopme());
		Servidor2.setPcirurgiasalas(dto.getPcirurgiasalas() != null ? dto.getPcirurgiasalas() : Servidor2.getPcirurgiasalas());
		Servidor2.setPcirurgialistaespera(dto.getPcirurgialistaespera() != null ? dto.getPcirurgialistaespera() : Servidor2.getPcirurgialistaespera());
		Servidor2.setPapoioassistencial(dto.getPapoioassistencial() != null ? dto.getPapoioassistencial() : Servidor2.getPapoioassistencial());
		Servidor2.setPcme(dto.getPcme() != null ? dto.getPcme() : Servidor2.getPcme());
		Servidor2.setPhemoterapia(dto.getPhemoterapia() != null ? dto.getPhemoterapia() : Servidor2.getPhemoterapia());
		Servidor2.setPsnd(dto.getPsnd() != null ? dto.getPsnd() : Servidor2.getPsnd());
		Servidor2.setPrnds(dto.getPrnds() != null ? dto.getPrnds() : Servidor2.getPrnds());
		Servidor2.setPnsp(dto.getPnsp() != null ? dto.getPnsp() : Servidor2.getPnsp());
		Servidor2.setPnspgestao(dto.getPnspgestao() != null ? dto.getPnspgestao() : Servidor2.getPnspgestao());
		Servidor2.setPnspclassificar(dto.getPnspclassificar() != null ? dto.getPnspclassificar() : Servidor2.getPnspclassificar());
		Servidor2.setPnspbloqueado(dto.getPnspbloqueado() != null ? dto.getPnspbloqueado() : Servidor2.getPnspbloqueado());
		Servidor2.setPfhirsumarioalta(dto.getPfhirsumarioalta() != null ? dto.getPfhirsumarioalta() : Servidor2.getPfhirsumarioalta());
		Servidor2.setLogs(dto.getLogs() != null ? dto.getLogs() : Servidor2.getLogs());
		Servidor2.setVigilanciaepidemiologica(dto.getVigilanciaepidemiologica() != null ? dto.getVigilanciaepidemiologica() : Servidor2.getVigilanciaepidemiologica());
		Servidor2.setConfiguracaovigilanciaepidemiologica(dto.getConfiguracaovigilanciaepidemiologica() != null ? dto.getConfiguracaovigilanciaepidemiologica() : Servidor2.getConfiguracaovigilanciaepidemiologica());
		Servidor2.setProducaomedica(dto.getProducaomedica() != null ? dto.getProducaomedica() : Servidor2.getProducaomedica());
		Servidor2.setIntegracaosus(dto.getIntegracaosus() != null ? dto.getIntegracaosus() : Servidor2.getIntegracaosus());
		Servidor2.setMigracaodados(dto.getMigracaodados() != null ? dto.getMigracaodados() : Servidor2.getMigracaodados());
		Servidor2.setCadleitos(dto.getCadleitos() != null ? dto.getCadleitos() : Servidor2.getCadleitos());
		Servidor2.setCadsetor(dto.getCadsetor() != null ? dto.getCadsetor() : Servidor2.getCadsetor());
		Servidor2.setRestaurarpaciente(dto.getRestaurarpaciente() != null ? dto.getRestaurarpaciente() : Servidor2.getRestaurarpaciente());
		Servidor2.setBiexecutivo(dto.getBiexecutivo() != null ? dto.getBiexecutivo() : Servidor2.getBiexecutivo());
		Servidor2.setNirrestaurarinternacao(dto.getNirrestaurarinternacao() != null ? dto.getNirrestaurarinternacao() : Servidor2.getNirrestaurarinternacao());
		Servidor2.setGed(dto.getGed() != null ? dto.getGed() : Servidor2.getGed());
		Servidor2.setGedremover(dto.getGedremover() != null ? dto.getGedremover() : Servidor2.getGedremover());
		Servidor2.setGedconsulta(dto.getGedconsulta() != null ? dto.getGedconsulta() : Servidor2.getGedconsulta());
		Servidor2.setCautela(dto.getCautela() != null ? dto.getCautela() : Servidor2.getCautela());
		Servidor2.setFarmaciaprodutos(dto.getFarmaciaprodutos() != null ? dto.getFarmaciaprodutos() : Servidor2.getFarmaciaprodutos());
		Servidor2.setFarmaciaestoque(dto.getFarmaciaestoque() != null ? dto.getFarmaciaestoque() : Servidor2.getFarmaciaestoque());
		Servidor2.setFarmaciadispensacao(dto.getFarmaciadispensacao() != null ? dto.getFarmaciadispensacao() : Servidor2.getFarmaciadispensacao());
		Servidor2.setFarmaciapermutas(dto.getFarmaciapermutas() != null ? dto.getFarmaciapermutas() : Servidor2.getFarmaciapermutas());
		Servidor2.setFarmaciaoperacoes(dto.getFarmaciaoperacoes() != null ? dto.getFarmaciaoperacoes() : Servidor2.getFarmaciaoperacoes());
		Servidor2.setFarmaciaavaliacao(dto.getFarmaciaavaliacao() != null ? dto.getFarmaciaavaliacao() : Servidor2.getFarmaciaavaliacao());
		Servidor2.setFarmaciarelatorios(dto.getFarmaciarelatorios() != null ? dto.getFarmaciarelatorios() : Servidor2.getFarmaciarelatorios());
		Servidor2.setRegrasdose(dto.getRegrasdose() != null ? dto.getRegrasdose() : Servidor2.getRegrasdose());
		Servidor2.setNirlaudointernacao(dto.getNirlaudointernacao() != null ? dto.getNirlaudointernacao() : Servidor2.getNirlaudointernacao());
		Servidor2.setNirenfermariaseleitos(dto.getNirenfermariaseleitos() != null ? dto.getNirenfermariaseleitos() : Servidor2.getNirenfermariaseleitos());
		Servidor2.setNirhistoricocompleto(dto.getNirhistoricocompleto() != null ? dto.getNirhistoricocompleto() : Servidor2.getNirhistoricocompleto());
		Servidor2.setNir(dto.getNir() != null ? dto.getNir() : Servidor2.getNir());
		Servidor2.setPainelnir(dto.getPainelnir() != null ? dto.getPainelnir() : Servidor2.getPainelnir());
		Servidor2.setLocalcenso(dto.getLocalcenso() != null ? dto.getLocalcenso() : Servidor2.getLocalcenso());
		Servidor2.setPesquisanir(dto.getPesquisanir() != null ? dto.getPesquisanir() : Servidor2.getPesquisanir());
		Servidor2.setNirrelatorio(dto.getNirrelatorio() != null ? dto.getNirrelatorio() : Servidor2.getNirrelatorio());
		Servidor2.setSetorcenso(dto.getSetorcenso() != null ? dto.getSetorcenso() : Servidor2.getSetorcenso());
		Servidor2.setInternadosvisitaimp(dto.getInternadosvisitaimp() != null ? dto.getInternadosvisitaimp() : Servidor2.getInternadosvisitaimp());
		Servidor2.setCcih(dto.getCcih() != null ? dto.getCcih() : Servidor2.getCcih());
		Servidor2.setBalancohidrico(dto.getBalancohidrico() != null ? dto.getBalancohidrico() : Servidor2.getBalancohidrico());
		Servidor2.setDriveenfermagem(dto.getDriveenfermagem() != null ? dto.getDriveenfermagem() : Servidor2.getDriveenfermagem());
		Servidor2.setEscalaenfermagem(dto.getEscalaenfermagem() != null ? dto.getEscalaenfermagem() : Servidor2.getEscalaenfermagem());
		Servidor2.setEvolucaomultidisciplinar(dto.getEvolucaomultidisciplinar() != null ? dto.getEvolucaomultidisciplinar() : Servidor2.getEvolucaomultidisciplinar());
		Servidor2.setPedidosexame(dto.getPedidosexame() != null ? dto.getPedidosexame() : Servidor2.getPedidosexame());
		Servidor2.setMinhasassinaturas(dto.getMinhasassinaturas() != null ? dto.getMinhasassinaturas() : Servidor2.getMinhasassinaturas());
		Servidor2.setAvaliacaopacienteia(dto.getAvaliacaopacienteia() != null ? dto.getAvaliacaopacienteia() : Servidor2.getAvaliacaopacienteia());
		Servidor2.setBulario(dto.getBulario() != null ? dto.getBulario() : Servidor2.getBulario());
		Servidor2.setBulariomedico(dto.getBulariomedico() != null ? dto.getBulariomedico() : Servidor2.getBulariomedico());
		Servidor2.setCiclomedicacao(dto.getCiclomedicacao() != null ? dto.getCiclomedicacao() : Servidor2.getCiclomedicacao());
		Servidor2.setHistoricocompleto(dto.getHistoricocompleto() != null ? dto.getHistoricocompleto() : Servidor2.getHistoricocompleto());
		Servidor2.setAnotacoesenfermagem(dto.getAnotacoesenfermagem() != null ? dto.getAnotacoesenfermagem() : Servidor2.getAnotacoesenfermagem());
		Servidor2.setSaeenfermagem(dto.getSaeenfermagem() != null ? dto.getSaeenfermagem() : Servidor2.getSaeenfermagem());
		Servidor2.setClassificacaoghbam(dto.getClassificacaoghbam() != null ? dto.getClassificacaoghbam() : Servidor2.getClassificacaoghbam());
		Servidor2.setClassificacaoghbam_revisar(dto.getClassificacaoghbam_revisar() != null ? dto.getClassificacaoghbam_revisar() : Servidor2.getClassificacaoghbam_revisar());
		Servidor2.setClassificacaoghbam_aprovar(dto.getClassificacaoghbam_aprovar() != null ? dto.getClassificacaoghbam_aprovar() : Servidor2.getClassificacaoghbam_aprovar());
		Servidor2.setClassificacaoghbam_admin(dto.getClassificacaoghbam_admin() != null ? dto.getClassificacaoghbam_admin() : Servidor2.getClassificacaoghbam_admin());
		Servidor2.setClassificacaoghbam_auditar(dto.getClassificacaoghbam_auditar() != null ? dto.getClassificacaoghbam_auditar() : Servidor2.getClassificacaoghbam_auditar());
		Servidor2.setControleassistencial(dto.getControleassistencial() != null ? dto.getControleassistencial() : Servidor2.getControleassistencial());
		Servidor2.setSinan(dto.getSinan() != null ? dto.getSinan() : Servidor2.getSinan());
		Servidor2.setDocumentosassistenciais(dto.getDocumentosassistenciais() != null ? dto.getDocumentosassistenciais() : Servidor2.getDocumentosassistenciais());
		Servidor2.setPainelenfermagem(dto.getPainelenfermagem() != null ? dto.getPainelenfermagem() : Servidor2.getPainelenfermagem());
		Servidor2.setPainelspa(dto.getPainelspa() != null ? dto.getPainelspa() : Servidor2.getPainelspa());
		Servidor2.setPainelmaternidade(dto.getPainelmaternidade() != null ? dto.getPainelmaternidade() : Servidor2.getPainelmaternidade());
		Servidor2.setPainelpediatria(dto.getPainelpediatria() != null ? dto.getPainelpediatria() : Servidor2.getPainelpediatria());
		Servidor2.setUltrassonografia(dto.getUltrassonografia() != null ? dto.getUltrassonografia() : Servidor2.getUltrassonografia());
		Servidor2.setModeloevolucaoenf(dto.getModeloevolucaoenf() != null ? dto.getModeloevolucaoenf() : Servidor2.getModeloevolucaoenf());
		Servidor2.setModeloprescricaoenf(dto.getModeloprescricaoenf() != null ? dto.getModeloprescricaoenf() : Servidor2.getModeloprescricaoenf());
		Servidor2.setResumopacientes(dto.getResumopacientes() != null ? dto.getResumopacientes() : Servidor2.getResumopacientes());
		Servidor2.setConsultorioadmin(dto.getConsultorioadmin() != null ? dto.getConsultorioadmin() : Servidor2.getConsultorioadmin());
		Servidor2.setConsultoriorecepcao(dto.getConsultoriorecepcao() != null ? dto.getConsultoriorecepcao() : Servidor2.getConsultoriorecepcao());
		Servidor2.setConsultoriomedico(dto.getConsultoriomedico() != null ? dto.getConsultoriomedico() : Servidor2.getConsultoriomedico());
		Servidor2.setConsultoriofinanceiro(dto.getConsultoriofinanceiro() != null ? dto.getConsultoriofinanceiro() : Servidor2.getConsultoriofinanceiro());
		Servidor2.setChatprofissionais(dto.getChatprofissionais() != null ? dto.getChatprofissionais() : Servidor2.getChatprofissionais());
		Servidor2.setMonitoramento(dto.getMonitoramento() != null ? dto.getMonitoramento() : Servidor2.getMonitoramento());
		Servidor2.setControleplantoes(dto.getControleplantoes() != null ? dto.getControleplantoes() : Servidor2.getControleplantoes());
		Servidor2.setProdutividaderh(dto.getProdutividaderh() != null ? dto.getProdutividaderh() : Servidor2.getProdutividaderh());
		Servidor2.setTreinamentocertificacao(dto.getTreinamentocertificacao() != null ? dto.getTreinamentocertificacao() : Servidor2.getTreinamentocertificacao());
		Servidor2.setGerenciarcertificados(dto.getGerenciarcertificados() != null ? dto.getGerenciarcertificados() : Servidor2.getGerenciarcertificados());
		Servidor2.setFaturamentosuslancar(dto.getFaturamentosuslancar() != null ? dto.getFaturamentosuslancar() : Servidor2.getFaturamentosuslancar());
		Servidor2.setFaturamentosuscorrigir(dto.getFaturamentosuscorrigir() != null ? dto.getFaturamentosuscorrigir() : Servidor2.getFaturamentosuscorrigir());
		Servidor2.setFaturamentosusauditar(dto.getFaturamentosusauditar() != null ? dto.getFaturamentosusauditar() : Servidor2.getFaturamentosusauditar());
		Servidor2.setFaturamentosusfechar(dto.getFaturamentosusfechar() != null ? dto.getFaturamentosusfechar() : Servidor2.getFaturamentosusfechar());
		Servidor2.setFaturamentosusreabrir(dto.getFaturamentosusreabrir() != null ? dto.getFaturamentosusreabrir() : Servidor2.getFaturamentosusreabrir());
		Servidor2.setSigtapimportar(dto.getSigtapimportar() != null ? dto.getSigtapimportar() : Servidor2.getSigtapimportar());
		Servidor2.setBpaexportar(dto.getBpaexportar() != null ? dto.getBpaexportar() : Servidor2.getBpaexportar());
		Servidor2.setApacautorizar(dto.getApacautorizar() != null ? dto.getApacautorizar() : Servidor2.getApacautorizar());
		Servidor2.setAihauditar(dto.getAihauditar() != null ? dto.getAihauditar() : Servidor2.getAihauditar());
		Servidor2.setFaturamentosusimportarretorno(dto.getFaturamentosusimportarretorno() != null ? dto.getFaturamentosusimportarretorno() : Servidor2.getFaturamentosusimportarretorno());
		Servidor2.setAps(dto.getAps() != null ? dto.getAps() : Servidor2.getAps());
		Servidor2.setApsacs(dto.getApsacs() != null ? dto.getApsacs() : Servidor2.getApsacs());
		Servidor2.setApsvacina(dto.getApsvacina() != null ? dto.getApsvacina() : Servidor2.getApsvacina());
		Servidor2.setApsgestao(dto.getApsgestao() != null ? dto.getApsgestao() : Servidor2.getApsgestao());
		Servidor2.setGpecatendimentos(dto.getGpecatendimentos() != null ? dto.getGpecatendimentos() : Servidor2.getGpecatendimentos());
		Servidor2.setGpeccidadaos(dto.getGpeccidadaos() != null ? dto.getGpeccidadaos() : Servidor2.getGpeccidadaos());
		Servidor2.setGpecagenda(dto.getGpecagenda() != null ? dto.getGpecagenda() : Servidor2.getGpecagenda());
		Servidor2.setGpecvacinacao(dto.getGpecvacinacao() != null ? dto.getGpecvacinacao() : Servidor2.getGpecvacinacao());
		Servidor2.setGpecterritorio(dto.getGpecterritorio() != null ? dto.getGpecterritorio() : Servidor2.getGpecterritorio());
		Servidor2.setGpecregistros(dto.getGpecregistros() != null ? dto.getGpecregistros() : Servidor2.getGpecregistros());
		Servidor2.setGpecacompanhamento(dto.getGpecacompanhamento() != null ? dto.getGpecacompanhamento() : Servidor2.getGpecacompanhamento());
		Servidor2.setGpecrelatorios(dto.getGpecrelatorios() != null ? dto.getGpecrelatorios() : Servidor2.getGpecrelatorios());
		Servidor2.setGpectransmissao(dto.getGpectransmissao() != null ? dto.getGpectransmissao() : Servidor2.getGpectransmissao());
		Servidor2.setGpecconfiguracao(dto.getGpecconfiguracao() != null ? dto.getGpecconfiguracao() : Servidor2.getGpecconfiguracao());
		Servidor2.setGpecappacs(dto.getGpecappacs() != null ? dto.getGpecappacs() : Servidor2.getGpecappacs());
		aplicarEstadoSuperusuario(Servidor2, eraSuperusuario);
		Servidor2.persist();
		securityPermissionService.invalidateUserCache(Servidor2.getMatricula());

	}

	private void aplicarEstadoSuperusuario(Servidor2 servidor2, boolean aplicarQuandoDesmarcado) {
		boolean superusuario = Boolean.TRUE.equals(servidor2.getSuperusuario());
		if (!superusuario && !aplicarQuandoDesmarcado) {
			return;
		}

		for (java.lang.reflect.Field field : Servidor2.class.getDeclaredFields()) {
			if (!Boolean.class.equals(field.getType()) || "superusuario".equals(field.getName())) {
				continue;
			}

			String setter = "set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
			try {
				Servidor2.class.getMethod(setter, Boolean.class).invoke(servidor2, superusuario);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Nao foi possivel aplicar permissao de super usuario em " + field.getName(), e);
			}
		}
	}

	public Servidor2 findByNomeDoMedico(String nome, String unidade, Servidor2Dto dto) {
		Servidor2 Servidor2 = new Servidor2();
		Servidor2 = servidor2Repository.findByNomeDoMedico(nome, unidade);
		if (Servidor2 == null) {
			System.out.println("Servidor2  nÃ£o foi encontrado...");
		}

		Servidor2.setMatricula(Servidor2.getMatricula());
		Servidor2.setNome(Servidor2.getNome());
		Servidor2.setTel(Servidor2.getTel());
		Servidor2.setEmail(Servidor2.getEmail());
		Servidor2.setCadsalas(dto.getCadsalas());
		Servidor2.setCadsetores(dto.getCadsetores());
		Servidor2.setCadunidades(dto.getCadunidades());
		Servidor2.setCadusuarios(dto.getCadusuarios());
		Servidor2.setAuditoriaclinica(dto.getAuditoriaclinica());
		Servidor2.setListausuarios(dto.getListausuarios());
		Servidor2.setMapapacientes(dto.getMapapacientes());
		Servidor2.setResumopaciente(dto.getResumopaciente());
		Servidor2.setAguardandoatendimento(dto.getAguardandoatendimento());
		Servidor2.setAtendimentoperiodo(dto.getAtendimentoperiodo());
		Servidor2.setBiometria(dto.getBiometria());
		Servidor2.setComunicacaoespecialistas(dto.getComunicacaoespecialistas());
		Servidor2.setCorrecaobam(dto.getCorrecaobam());
		Servidor2.setEnfermariaseleitos(dto.getEnfermariaseleitos());
		Servidor2.setImprimirbam(dto.getImprimirbam());
		Servidor2.setLimparpainel(dto.getLimparpainel());
		Servidor2.setPacientesematendimento(dto.getPacientesematendimento());
		Servidor2.setCadleitos(dto.getCadleitos());
		Servidor2.setCadsetor(dto.getCadsetor());
		Servidor2.setCcih(dto.getCcih());
		Servidor2.setBalancohidrico(dto.getBalancohidrico());
		Servidor2.setDriveenfermagem(dto.getDriveenfermagem());
		Servidor2.setEscalaenfermagem(dto.getEscalaenfermagem());
		Servidor2.setEvolucaomultidisciplinar(dto.getEvolucaomultidisciplinar());
		Servidor2.setPedidosexame(dto.getPedidosexame());
		return Servidor2;

	}

	public Servidor2 servidor2Matricula(String matricula) {
		
		Servidor2 servidor2 = servidor2Repository.localizarMatricula(matricula);
		if (servidor2 == null) {
			System.out.println("MatrÃ­cula nÃ£o foi encontrada...");
			return null;
		}
		// Os campos jÃ¡ estÃ£o preenchidos pelo prÃ³prio objeto recuperado do banco
		return servidor2;
	}

	public Servidor2 Servidor2MatriculaBiometria(String matricula, Servidor2Dto dto) {
		Servidor2 Servidor2 = new Servidor2();
		Servidor2 = servidor2Repository.localizarMatriculaBiometria(matricula);
		if (Servidor2 == null) {
			System.out.println("MatrÃ­cula nÃ£o foi encontrada...");
			return null;
		}

		Servidor2.setMatricula(Servidor2.getMatricula());
		Servidor2.setNome(Servidor2.getNome());
		Servidor2.setTel(Servidor2.getTel());
		Servidor2.setEmail(Servidor2.getEmail());
		Servidor2.setCadsalas(dto.getCadsalas());
		Servidor2.setCadsetores(dto.getCadsetores());
		Servidor2.setCadunidades(dto.getCadunidades());
		Servidor2.setCadusuarios(dto.getCadusuarios());
		Servidor2.setAuditoriaclinica(dto.getAuditoriaclinica());
		Servidor2.setListausuarios(dto.getListausuarios());
		Servidor2.setMapapacientes(dto.getMapapacientes());
		Servidor2.setResumopaciente(dto.getResumopaciente());
		Servidor2.setAguardandoatendimento(dto.getAguardandoatendimento());
		Servidor2.setAtendimentoperiodo(dto.getAtendimentoperiodo());
		Servidor2.setBiometria(dto.getBiometria());
		Servidor2.setComunicacaoespecialistas(dto.getComunicacaoespecialistas());
		Servidor2.setCorrecaobam(dto.getCorrecaobam());
		Servidor2.setEnfermariaseleitos(dto.getEnfermariaseleitos());
		Servidor2.setImprimirbam(dto.getImprimirbam());
		Servidor2.setLimparpainel(dto.getLimparpainel());
		Servidor2.setPacientesematendimento(dto.getPacientesematendimento());
		Servidor2.setCadleitos(dto.getCadleitos());
		Servidor2.setCadsetor(dto.getCadsetor());
		Servidor2.setCcih(dto.getCcih());
		Servidor2.setBalancohidrico(dto.getBalancohidrico());
		Servidor2.setDriveenfermagem(dto.getDriveenfermagem());
		Servidor2.setEscalaenfermagem(dto.getEscalaenfermagem());
		Servidor2.setEvolucaomultidisciplinar(dto.getEvolucaomultidisciplinar());
		Servidor2.setPedidosexame(dto.getPedidosexame());
		return Servidor2;

	}

	public void removeServidor2(Long id) {
		Optional<Servidor2> Servidor2Op = Setores.findByIdOptional(id);
		if (Servidor2Op.isEmpty()) {
			System.out.println("Servidor2 nÃ£o foi encontrado...");
		}

		Servidor2 Servidor2 = Servidor2Op.get();
		securityPermissionService.invalidateUserCache(Servidor2.getMatricula());
		Servidor2.delete();

	}

	public Servidor2 verificarBiometria(String fir) {
		// Essa forma estÃ¡ OK
		return servidor2Repository.find("biometria", fir).firstResult();
	}

}
