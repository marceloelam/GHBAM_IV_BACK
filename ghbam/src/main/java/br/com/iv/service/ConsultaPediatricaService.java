package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;

import br.com.iv.dto.ConsultaPediatricaDto;
import br.com.iv.entity.ConsultaPediatrica;
import br.com.iv.repository.ConsultaPediatricaRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class ConsultaPediatricaService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ConsultaPediatricaRepository consultaPediatricaRepository;

	public List<ConsultaPediatrica> listarPorPaciente(int idpaciente) {
		return consultaPediatricaRepository.listarPorPaciente(idpaciente);
	}

	public List<ConsultaPediatrica> listarPorProntuario(String prontuario) {
		return consultaPediatricaRepository.listarPorProntuario(prontuario);
	}

	public List<ConsultaPediatrica> listarPorIdbam(int idbam) {
		return consultaPediatricaRepository.listarPorIdbam(idbam);
	}

	public ConsultaPediatrica buscarPorId(int id) {
		return ConsultaPediatrica.findById(id);
	}

	@Transactional
	public ConsultaPediatrica salvar(ConsultaPediatricaDto dto, AuthenticatedUser usuario) {
		ConsultaPediatrica consulta = new ConsultaPediatrica();
		copiarDtoParaEntity(dto, consulta);
		consulta.setDataConsulta(new Date());
		consulta.setCriadoPorMatricula(usuario == null ? "" : usuario.matricula());
		consulta.persist();
		return consulta;
	}

	@Transactional
	public void atualizar(int id, ConsultaPediatricaDto dto, AuthenticatedUser usuario) {
		Optional<ConsultaPediatrica> consultaOp = ConsultaPediatrica.findByIdOptional(id);
		ConsultaPediatrica consulta = consultaOp
				.orElseThrow(() -> new NullPointerException("Consulta pediátrica não encontrada."));
		validarPermissaoAlteracao(consulta, usuario);
		copiarDtoParaEntity(dto, consulta);
		consulta.persist();
	}

	@Transactional
	public void excluir(int id, AuthenticatedUser usuario) {
		Optional<ConsultaPediatrica> consultaOp = ConsultaPediatrica.findByIdOptional(id);
		ConsultaPediatrica consulta = consultaOp
				.orElseThrow(() -> new NullPointerException("Consulta pediátrica não encontrada."));
		validarPermissaoAlteracao(consulta, usuario);
		consulta.delete();
	}

	private void validarPermissaoAlteracao(ConsultaPediatrica consulta, AuthenticatedUser usuario) {
		String matricula = usuario == null ? "" : usuario.matricula();
		String matriculaCriador = consulta.getCriadoPorMatricula();
		if (matricula.isBlank() || matriculaCriador == null || !matricula.equals(matriculaCriador)) {
			throw new ForbiddenException("Apenas o medico que criou este registro pode altera-lo ou exclui-lo.");
		}
		LocalDate hoje = LocalDate.now();
		LocalDate diaCriacao = consulta.getCriadoEm() == null
				? null
				: consulta.getCriadoEm().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		if (diaCriacao == null || !diaCriacao.equals(hoje)) {
			throw new IllegalArgumentException(
					"Este registro so pode ser alterado ou excluido no mesmo dia em que foi criado.");
		}
	}

	private void copiarDtoParaEntity(ConsultaPediatricaDto dto, ConsultaPediatrica consulta) {
		consulta.setIdbam(dto.getIdbam());
		consulta.setIdpaciente(dto.getIdpaciente());
		consulta.setProntuario(dto.getProntuario());
		consulta.setNbam(dto.getNbam());
		consulta.setUnidade(dto.getUnidade());
		consulta.setIdadeMeses(dto.getIdadeMeses());

		consulta.setPeso(dto.getPeso());
		consulta.setEstatura(dto.getEstatura());
		consulta.setPerimetroCefalico(dto.getPerimetroCefalico());

		consulta.setIdadeGestacionalSemanas(dto.getIdadeGestacionalSemanas());
		consulta.setTipoParto(dto.getTipoParto());
		consulta.setPesoNascimento(dto.getPesoNascimento());
		consulta.setEstaturaNascimento(dto.getEstaturaNascimento());
		consulta.setPerimetroCefalicoNascimento(dto.getPerimetroCefalicoNascimento());
		consulta.setApgar1(dto.getApgar1());
		consulta.setApgar5(dto.getApgar5());

		consulta.setNumeroGestacoes(dto.getNumeroGestacoes());
		consulta.setNumeroPartos(dto.getNumeroPartos());
		consulta.setNumeroAbortos(dto.getNumeroAbortos());
		consulta.setFumouGestacao(dto.getFumouGestacao());
		consulta.setHiv(dto.getHiv());
		consulta.setRubeola(dto.getRubeola());
		consulta.setToxoplasmose(dto.getToxoplasmose());
		consulta.setCmv(dto.getCmv());
		consulta.setSifilis(dto.getSifilis());
		consulta.setOutrosAntecedentesGestacionais(dto.getOutrosAntecedentesGestacionais());
		consulta.setIntercorrenciasGestacao(dto.getIntercorrenciasGestacao());
		consulta.setMedicacoesGestacao(dto.getMedicacoesGestacao());

		consulta.setPermanenciaNeonatal(dto.getPermanenciaNeonatal());
		consulta.setPesoAlta(dto.getPesoAlta());
		consulta.setDeltaPesoAlta(dto.getDeltaPesoAlta());
		consulta.setPerimetroToracico(dto.getPerimetroToracico());
		consulta.setClassificacaoPeso(dto.getClassificacaoPeso());
		consulta.setIntercorrenciasNeonatais(dto.getIntercorrenciasNeonatais());
		consulta.setIntercorrenciasNeonataisObs(dto.getIntercorrenciasNeonataisObs());
		consulta.setClassificacaoIdadeGestacional(dto.getClassificacaoIdadeGestacional());

		consulta.setTriagemAuditiva(dto.getTriagemAuditiva());
		consulta.setReflexoVermelho(dto.getReflexoVermelho());
		consulta.setTipagemSanguineaMae(dto.getTipagemSanguineaMae());
		consulta.setTipagemSanguineaRn(dto.getTipagemSanguineaRn());
		consulta.setCoombs(dto.getCoombs());
		consulta.setEluato(dto.getEluato());
		consulta.setOximetria(dto.getOximetria());
		consulta.setQuedaCoto(dto.getQuedaCoto());
		consulta.setTestePezinho(dto.getTestePezinho());
		consulta.setTesteLinguinha(dto.getTesteLinguinha());
		consulta.setDiagnosticoNeonatalOutros(dto.getDiagnosticoNeonatalOutros());

		consulta.setFicaEmCasa(dto.getFicaEmCasa());
		consulta.setTomaSol(dto.getTomaSol());
		consulta.setHabitoOral(dto.getHabitoOral());
		consulta.setHoraAcorda(dto.getHoraAcorda());
		consulta.setChupeta(dto.getChupeta());
		consulta.setMamadeira(dto.getMamadeira());
		consulta.setVaiEscola(dto.getVaiEscola());
		consulta.setPeriodoEscola(dto.getPeriodoEscola());
		consulta.setComoSono(dto.getComoSono());
		consulta.setPraticaEsporte(dto.getPraticaEsporte());
		consulta.setFazEducacaoFisica(dto.getFazEducacaoFisica());
		consulta.setSedentarismo(dto.getSedentarismo());
		consulta.setDiureseEvacuacao(dto.getDiureseEvacuacao());
		consulta.setHigieneBucal(dto.getHigieneBucal());

		consulta.setTipoAleitamento(dto.getTipoAleitamento());
		consulta.setComeCarnes(dto.getComeCarnes());
		consulta.setComeOvos(dto.getComeOvos());
		consulta.setComeVerdurasLegumes(dto.getComeVerdurasLegumes());
		consulta.setComeLeites(dto.getComeLeites());
		consulta.setComeFrutas(dto.getComeFrutas());
		consulta.setComeDocesGuloseimas(dto.getComeDocesGuloseimas());
		consulta.setAlimentacaoOutros(dto.getAlimentacaoOutros());

		consulta.setTemperatura(dto.getTemperatura());
		consulta.setPressaoSentadoSistolica(dto.getPressaoSentadoSistolica());
		consulta.setPressaoSentadoDiastolica(dto.getPressaoSentadoDiastolica());
		consulta.setPressaoDeitadoSistolica(dto.getPressaoDeitadoSistolica());
		consulta.setPressaoDeitadoDiastolica(dto.getPressaoDeitadoDiastolica());
		consulta.setCircunferenciaAbdominal(dto.getCircunferenciaAbdominal());
		consulta.setAlturaPai(dto.getAlturaPai());
		consulta.setAlturaMae(dto.getAlturaMae());
		consulta.setAspectoGeral(dto.getAspectoGeral());
		consulta.setOtoscopia(dto.getOtoscopia());
		consulta.setCavidadeOral(dto.getCavidadeOral());
		consulta.setMamas(dto.getMamas());
		consulta.setCardiovascular(dto.getCardiovascular());
		consulta.setRespiratorio(dto.getRespiratorio());
		consulta.setAbdome(dto.getAbdome());
		consulta.setGenital(dto.getGenital());
		consulta.setExtremidades(dto.getExtremidades());
		consulta.setExameOutrasObservacoes(dto.getExameOutrasObservacoes());

		consulta.setDiagnosticoNutricional(dto.getDiagnosticoNutricional());
		consulta.setNutricaoAdequada(dto.getNutricaoAdequada());
		consulta.setCrescimentoAdequado(dto.getCrescimentoAdequado());
		consulta.setVacinacaoAtualizada(dto.getVacinacaoAtualizada());

		consulta.setQueixaPrincipal(dto.getQueixaPrincipal());
		consulta.setHda(dto.getHda());
		consulta.setAlimentacao(dto.getAlimentacao());
		consulta.setHistoricoVacinal(dto.getHistoricoVacinal());
		consulta.setHistoricoFamiliar(dto.getHistoricoFamiliar());

		consulta.setDnpmMotorGrosso(dto.getDnpmMotorGrosso());
		consulta.setDnpmMotorFino(dto.getDnpmMotorFino());
		consulta.setDnpmLinguagem(dto.getDnpmLinguagem());
		consulta.setDnpmPessoalSocial(dto.getDnpmPessoalSocial());
		consulta.setDnpmObservacao(dto.getDnpmObservacao());

		consulta.setHipoteseDiagnostica(dto.getHipoteseDiagnostica());
		consulta.setCid(dto.getCid());
		consulta.setConduta(dto.getConduta());

		consulta.setMedico(dto.getMedico());
		consulta.setCrm(dto.getCrm());
	}

}
