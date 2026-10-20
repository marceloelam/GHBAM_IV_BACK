package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Event;
import br.com.iv.dto.AfaturarDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.Pacientes;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.repository.AfaturarRepository;
import br.com.iv.repository.PacientesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AfaturarService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	AfaturarRepository afaturarRepository;

	@Inject
	PacientesRepository pacientesRepository;

	@Inject
	FaturamentoProducaoService faturamentoProducaoService;

	@Inject
	br.com.iv.repository.FaturamentoProducaoRepository producaoRepository;

	@Inject
	Event<Afaturar> faturamentoPersistido;

	@Transactional
	public List<Afaturar> filtarPorAfaturarPeriodo(String d1, String d2, String unidade) {

		List<Afaturar> afaturarOp = afaturarRepository.filtarPorPeriodoEunidade(d1, d2, unidade);
		if (afaturarOp.isEmpty()) {
			System.out.println("Fatura do período não foi encontrada...");
		}
		preencherDadosPacienteParaBpa(afaturarOp);
		return afaturarOp;
	}

	private void preencherDadosPacienteParaBpa(List<Afaturar> producoes) {
		if (producoes == null || producoes.isEmpty()) {
			return;
		}
		List<Integer> idsPaciente = producoes.stream()
				.map(Afaturar::getIdpaciente)
				.filter(id -> id > 0)
				.distinct()
				.toList();
		if (idsPaciente.isEmpty()) {
			return;
		}
		Map<Integer, Pacientes> pacientesPorId = pacientesRepository
				.list("idpaciente in ?1", idsPaciente)
				.stream()
				.collect(Collectors.toMap(Pacientes::getIdpaciente, Function.identity(), (a, b) -> a));
		producoes.forEach(producao -> {
			Pacientes paciente = pacientesPorId.get(producao.getIdpaciente());
			if (paciente == null) {
				return;
			}
			producao.setCpfPaciente(paciente.getCpf());
			producao.setNascimentoPaciente(paciente.getNascimento());
			producao.setMaePaciente(paciente.getMae());
			producao.setMunicipioPaciente(paciente.getNaturalidade());
		});
	}

	@Transactional
	// Salvar Afaturar
	public Afaturar salvarFatura(AfaturarDto dto) {
		return salvarFatura(dto, null);
	}

	@Transactional
	// Salvar Afaturar
	public Afaturar salvarFatura(AfaturarDto dto, AuthenticatedUser user) {
		if (dto == null) {
			throw new IllegalArgumentException("Dados de faturamento ausentes");
		}
		if (dto.getProcedimento() == null || dto.getProcedimento().isBlank()) {
			throw new IllegalArgumentException("Procedimento é obrigatório");
		}

		// Idempotencia por origem: chamado duas vezes para o mesmo (origemModulo,
		// origemId) - duplo clique, tela reenviando, retentativa de rede - devolve a
		// producao que ja existe em vez de criar outra. Producao SUS duplicada nao e
		// so ruido de tela: vira rejeicao da remessa e apontamento de auditoria.
		//
		// Devolve a linha existente sem reescrever os dados de proposito. Se o
		// faturamento ja corrigiu o CNS ou o CBO dela, reenviar o lancamento nao pode
		// desfazer a correcao trazendo de volta o valor que veio da tela.
		String origemModulo = dto.getOrigemModulo() == null ? "" : dto.getOrigemModulo().trim();
		Integer origemId = dto.getOrigemId();
		if (!origemModulo.isBlank() && origemId != null) {
			Afaturar existente = producaoRepository.buscarPorOrigem(origemModulo, origemId);
			if (existente != null) {
				faturamentoPersistido.fire(existente);
				return existente;
			}
		}
		Afaturar afaturar = new Afaturar();

		afaturar.setProcedimento(dto.getProcedimento());
		afaturar.setDescricao(dto.getDescricao());
		afaturar.setLocal(dto.getLocal());
		afaturar.setLocalsolicitante(dto.getLocalsolicitante());
		afaturar.setMatriculaservidor(dto.getMatriculaservidor());
		afaturar.setNomeservidor(dto.getNomeservidor());
		afaturar.setBam(dto.getBam());
		afaturar.setData(dto.getData());
		afaturar.setIdade(dto.getIdade());
		afaturar.setIdtratamento(dto.getIdtratamento());
		afaturar.setIdregistro(dto.getIdregistro());
		afaturar.setControleid(dto.getControleid());
		afaturar.setIdpaciente(dto.getIdpaciente());		
		afaturar.setPa_sa(dto.getPa_sa()); // Ambulatorial valor
		afaturar.setPa_sp(dto.getPa_sp()); // profissional valor
		afaturar.setPa_sh(dto.getPa_sh()); // Hospitalar valor
		afaturar.setPa_tpreg(dto.getPa_tpreg()); // 01 - consolidado, 02 - individualizado e outros
		afaturar.setPa_eletiva(dto.getPa_eletiva());
		// para BPA individualizado
		afaturar.setCnsdoprofissional(dto.getCnsdoprofissional());
		afaturar.setCnsdopciente(dto.getCnsdopciente());
		afaturar.setSexo(dto.getSexo());
		afaturar.setIbgecod(dto.getIbgecod()); // código ibge do município da residência
		afaturar.setDtatendimento(dto.getDtatendimento());
		afaturar.setCid10(dto.getCid10());
		afaturar.setCarateratend(dto.getCarateratend());
		afaturar.setAutorizacao(dto.getAutorizacao()); // número de autorização do estabelecimento
		afaturar.setOrigeminfo(dto.getOrigeminfo()); // bpa, ini, sie, sib, min, pac, scl. ext
		afaturar.setNomepaciente(dto.getNomepaciente());
		afaturar.setDtnascimento(br.com.iv.util.DateUtils.toYMD(dto.getDtnascimento()));
		afaturar.setCbo(dto.getCbo());
		afaturar.setRaca_cor(dto.getRaca_cor());
		afaturar.setEtnia_raca(dto.getEtnia_raca());
		afaturar.setNacionalidade(dto.getNacionalidade());
		afaturar.setCodservico(dto.getCodservico());
		afaturar.setCodclassificacao(dto.getCodclassificacao());
		afaturar.setCod_seq_equipe(dto.getCod_seq_equipe());
		afaturar.setCod_area_equipe(dto.getCod_area_equipe());
		afaturar.setCod_cnpj(dto.getCod_cnpj());
		afaturar.setCep(dto.getCep());
		afaturar.setCod_logradouro(dto.getCod_logradouro());
		afaturar.setEndereco(dto.getEndereco());
		afaturar.setComplemento(dto.getComplemento());
		afaturar.setNumerocasa(dto.getNumerocasa());
		afaturar.setBairro(dto.getBairro());
		afaturar.setTelefone(dto.getTelefone());
		afaturar.setEmail(dto.getEmail());
		afaturar.setIdentnacequipe(dto.getIdentnacequipe());
		afaturar.setUnidade(dto.getUnidade());
		if (!origemModulo.isBlank() && origemId != null) {
			afaturar.setOrigemModulo(origemModulo);
			afaturar.setOrigemId(origemId);
		}
		// A producao passa pelo motor antes de ser gravada: competencia vinculada, SIGTAP
		// relido, CNS/CBO herdados do cadastro e validacao aplicada. Antes daqui ela nascia
		// APTO_FATURAMENTO sem nunca ter sido validada - ver
		// FaturamentoProducaoService.prepararProducaoNova.
		faturamentoProducaoService.prepararProducaoNova(afaturar, user);
		afaturar.persist();
		faturamentoPersistido.fire(afaturar);
		return afaturar;
	}

}

