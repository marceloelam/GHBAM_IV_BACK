package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ConfiguracaoUnidadeDto;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

@ApplicationScoped
public class ConfiguracaoUnidadeService implements Serializable {
	private static final long serialVersionUID = 1L;
	String destination;
	String nomeArquivo;

	@Context
	UriInfo uriInfo;

	@Inject
	ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

	// Listando todos os setores
	public List<ConfiguracaoUnidade> listarTodosHospitais() {
		return configuracaoUnidadeRepository.listAll();
	}

	// Filtar por setor e unidade
	@Transactional
	public List<ConfiguracaoUnidade> filtarPorUnidadeHospitalar(String hospital, ConfiguracaoUnidadeDto dto) {

		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		List<ConfiguracaoUnidade> configuracaoUnidadeOp = configuracaoUnidadeRepository
				.filtarPorUnidadeConfiguracao(hospital);
		if (configuracaoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return configuracaoUnidadeOp;
	}

	// Filtar por sigla
	@Transactional
	public List<ConfiguracaoUnidade> filtarPorSigladaUnidade(String sigla, ConfiguracaoUnidadeDto dto) {

		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		List<ConfiguracaoUnidade> configuracaoUnidadeOp = configuracaoUnidadeRepository.filtarPorUnidadeSigla(sigla);
		if (configuracaoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return configuracaoUnidadeOp;
	}

	public List<ConfiguracaoUnidade> filtarPorSiglaUnidadeHospitalar() {
		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		List<ConfiguracaoUnidade> configuracaoUnidadeOp = configuracaoUnidadeRepository
				.listarUnidadesDistintosDoSetor();
		if (configuracaoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return configuracaoUnidadeOp;
	}

	// Salvar os setores
	@Transactional
	public ConfiguracaoUnidade salvarUnidadeHospitalar(ConfiguracaoUnidadeDto dto) {

		ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		configuracaoUnidade.setPrefeitura(dto.getPrefeitura());
		configuracaoUnidade.setComplemetos(dto.getComplemetos());
		configuracaoUnidade.setHospital(dto.getHospital());
		configuracaoUnidade.setHospitalBPA(dto.getHospitalBPA());
		configuracaoUnidade.setMunicipioPadrao(dto.getMunicipioPadrao());
		configuracaoUnidade.setBairroPadrao(dto.getBairroPadrao());
		configuracaoUnidade.setSigla(dto.getSigla());
		configuracaoUnidade.setCodigoGestor(dto.getCodigoGestor());
		configuracaoUnidade.setCnesDoHospital(dto.getCnesDoHospital());
		configuracaoUnidade.setCnpjDoHospital(dto.getCnpjDoHospital());
		configuracaoUnidade.setSetorPadrao(dto.getSetorPadrao());
		configuracaoUnidade.setTipoUnidade(normalizarTipoUnidade(dto.getTipoUnidade()));
		configuracaoUnidade.setCodIbge(dto.getCodIbge());
		configuracaoUnidade.setRegistroBPA(dto.getRegistroBPA());
		configuracaoUnidade.setOrgaoDestino(dto.getOrgaoDestino());
		configuracaoUnidade.setIndicadorBPA(dto.getIndicadorBPA());
		configuracaoUnidade.setVersaoBPA(dto.getVersaoBPA());
		configuracaoUnidade.setLongitude(dto.getLongitude());
		configuracaoUnidade.setLatitude(dto.getLatitude());
		configuracaoUnidade.setCep(dto.getCep());
		configuracaoUnidade.setLogoprefeitura(dto.getLogoprefeitura());
	    configuracaoUnidade.setBrasaoprefeitura(dto.getBrasaoprefeitura());
		configuracaoUnidade.setUrlconexaolaudopacs(dto.getUrlconexaolaudopacs());
		configuracaoUnidade.setUrlconexaoimagempacs(dto.getUrlconexaoimagempacs());
		configuracaoUnidade.setMonaiHabilitado(dto.getMonaiHabilitado());
		configuracaoUnidade.setMonaiUrl(dto.getMonaiUrl());
		configuracaoUnidade.setOrthancUrl(dto.getOrthancUrl());
		if (dto.getTokenlaudopacs() != null && !dto.getTokenlaudopacs().isBlank()) {
			configuracaoUnidade.setTokenlaudopacs(dto.getTokenlaudopacs());
		}
		if (dto.getTokenimagempacs() != null && !dto.getTokenimagempacs().isBlank()) {
			configuracaoUnidade.setTokenimagempacs(dto.getTokenimagempacs());
		}
		configuracaoUnidade.setUrlConexao(dto.getUrlConexao());
		configuracaoUnidade.setUserConexao(dto.getUserConexao());
		if (dto.getPwdConexao() != null && !dto.getPwdConexao().isBlank()) {
			configuracaoUnidade.setPwdConexao(dto.getPwdConexao());
		}
		configuracaoUnidade.setLinkvideopainel(dto.getLinkvideopainel());

		configuracaoUnidade.persist();
		return configuracaoUnidade;
	}

	// Alterar
	@SuppressWarnings("static-access")
	@Transactional
	public void atualizarSetor(int id, ConfiguracaoUnidadeDto dto) {
		Optional<ConfiguracaoUnidade> configuracaoUnidadeOp = ConfiguracaoUnidade.findByIdOptional(id);
		if (configuracaoUnidadeOp.isEmpty()) {
			throw new EntityNotFoundException("Unidade hospitalar não foi encontrada...");
		}

		ConfiguracaoUnidade configuracaoUnidade = configuracaoUnidadeOp.get();
		configuracaoUnidade.setPrefeitura(dto.getPrefeitura());
		configuracaoUnidade.setComplemetos(dto.getComplemetos());
		configuracaoUnidade.setHospital(dto.getHospital());
		configuracaoUnidade.setHospitalBPA(dto.getHospitalBPA());
		configuracaoUnidade.setMunicipioPadrao(dto.getMunicipioPadrao());
		configuracaoUnidade.setBairroPadrao(dto.getBairroPadrao());
		configuracaoUnidade.setSigla(dto.getSigla());
		configuracaoUnidade.setCodigoGestor(dto.getCodigoGestor());
		configuracaoUnidade.setCnesDoHospital(dto.getCnesDoHospital());
		configuracaoUnidade.setCnpjDoHospital(dto.getCnpjDoHospital());
		configuracaoUnidade.setSetorPadrao(dto.getSetorPadrao());
		configuracaoUnidade.setTipoUnidade(normalizarTipoUnidade(dto.getTipoUnidade()));
		configuracaoUnidade.setCodIbge(dto.getCodIbge());
		configuracaoUnidade.setRegistroBPA(dto.getRegistroBPA());
		configuracaoUnidade.setOrgaoDestino(dto.getOrgaoDestino());
		configuracaoUnidade.setIndicadorBPA(dto.getIndicadorBPA());
		configuracaoUnidade.setVersaoBPA(dto.getVersaoBPA());
		configuracaoUnidade.setLongitude(dto.getLongitude());
		configuracaoUnidade.setLatitude(dto.getLatitude());
		configuracaoUnidade.setCep(dto.getCep());
		configuracaoUnidade.setLogoprefeitura(dto.getLogoprefeitura());
		configuracaoUnidade.setBrasaoprefeitura(dto.getBrasaoprefeitura());
		configuracaoUnidade.setUrlconexaolaudopacs(dto.getUrlconexaolaudopacs());
		configuracaoUnidade.setUrlconexaoimagempacs(dto.getUrlconexaoimagempacs());
		configuracaoUnidade.setMonaiHabilitado(dto.getMonaiHabilitado());
		configuracaoUnidade.setMonaiUrl(dto.getMonaiUrl());
		configuracaoUnidade.setOrthancUrl(dto.getOrthancUrl());
		if (dto.getTokenlaudopacs() != null && !dto.getTokenlaudopacs().isBlank()) {
			configuracaoUnidade.setTokenlaudopacs(dto.getTokenlaudopacs());
		}
		if (dto.getTokenimagempacs() != null && !dto.getTokenimagempacs().isBlank()) {
			configuracaoUnidade.setTokenimagempacs(dto.getTokenimagempacs());
		}
		configuracaoUnidade.setUrlConexao(dto.getUrlConexao());
		configuracaoUnidade.setUserConexao(dto.getUserConexao());
		if (dto.getPwdConexao() != null && !dto.getPwdConexao().isBlank()) {
			configuracaoUnidade.setPwdConexao(dto.getPwdConexao());
		}
		configuracaoUnidade.setLinkvideopainel(dto.getLinkvideopainel());

		// Persiste as alterações
		configuracaoUnidade.persist();
	}

	@Transactional
	public void removeSetor(Long id) {
		Optional<ConfiguracaoUnidade> configuracaoUnidadeOp = ConfiguracaoUnidade.findByIdOptional(id);
		if (configuracaoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}

		ConfiguracaoUnidade configuracaoUnidade = configuracaoUnidadeOp.get();
		configuracaoUnidade.delete();

	}



	private String normalizarTipoUnidade(String tipoUnidade) {
		String tipo = tipoUnidade == null ? "" : tipoUnidade.trim().toUpperCase();
		if (tipo.equals("UPA") || tipo.equals("CONSULTORIO") || tipo.equals("CLINICA_ESPECIALIZADA")) {
			return tipo;
		}
		return "HOSPITAL";
	}
}
