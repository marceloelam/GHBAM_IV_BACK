package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;

import br.com.iv.dto.AssinaturaClinicaDto;
import br.com.iv.dto.AtendimentoMultidisciplinarDto;
import br.com.iv.dto.AtendimentoMultidisciplinarFilaDto;
import br.com.iv.entity.AtendimentoMultidisciplinar;
import br.com.iv.repository.AtendimentoMultidisciplinarRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class AtendimentoMultidisciplinarService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	AtendimentoMultidisciplinarRepository repository;

	@Inject
	EntityManager entityManager;

	@Inject
	AssinaturaClinicaService assinaturaClinicaService;

	// Fila e uma adaptacao FIEL de AtendimentoMedicoRepository.medicoAtenderHoje —
	// a query comprovada por tras da tabela "Pacientes Aguardando Atendimento" do
	// AtMedico (endpoint /atendidopor/, acionado pelo botao "Atualizar" daquela
	// tela) — sem o filtro que exclui setores multidisciplinares de la e sem o
	// CASE WHEN de atendidopor (que so importa para o mesmo medico reabrir um
	// exame que ele proprio esta com resultado em andamento).
	@SuppressWarnings("unchecked")
	public List<AtendimentoMultidisciplinarFilaDto> filaMultidisciplinar(String atenderHoje, String unidade,
			String setorPadrao) {
		List<AtendimentoMultidisciplinarFilaDto> resultado = new ArrayList<>();
		if (atenderHoje == null || atenderHoje.isBlank() || unidade == null || unidade.isBlank()) {
			return resultado;
		}

		String sql = """
			SELECT DISTINCT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento,
			       a.idavaliacao, a.avaliado, b.idbam, b.hora, b.cor,
			       r.idregistro, r.setor, t.idtratamento, t.tratado
			FROM tratamento t, avaliacao a, pacientes p, bam b, registro r, escalapaciente e
			WHERE b.idbam = e.agbam AND e.agenda = false
			  AND b.ematendimento = false
			  AND b.registro_idregistro = r.idregistro
			  AND b.tratamento_idtratamento = t.idtratamento
			  AND b.paciente_idpaciente = p.idpaciente
			  AND b.avaliacao_idavaliacao = a.idavaliacao
			  AND t.tratado = 0
			  AND t.tipo_alta NOT IN ('internacao', 'obs')
			  AND r.eletivo = false
			  AND a.esperando = 0
			  AND CASE WHEN (r.setor IN (
			        SELECT DISTINCT ON (setoresunidade.setor) setoresunidade.setor FROM setoresunidade
			        INNER JOIN registro ON registro.setor = setoresunidade.setor
			        WHERE setoresunidade.triar = true AND setoresunidade.siglaunidade = :unidade
			      )) THEN a.avaliado = 1 ELSE a.avaliado = 0 END
			  AND translate(UPPER(r.setor), 'ÁÀÃÂÄÉÈÊËÍÌÎÏÓÒÕÔÖÚÙÛÜÇÑ', 'AAAAAEEEEIIIIOOOOOUUUUCN')
			        LIKE translate(UPPER(:atenderHoje), 'ÁÀÃÂÄÉÈÊËÍÌÎÏÓÒÕÔÖÚÙÛÜÇÑ', 'AAAAAEEEEIIIIOOOOOUUUUCN') || '%'
			  AND CAST(b.data AS date) BETWEEN (CURRENT_DATE - INTERVAL '1 day') AND CURRENT_DATE
			  AND UPPER(b.unidade) = UPPER(:unidade)
			ORDER BY b.cor DESC, b.hora
			""";

		var query = entityManager.createNativeQuery(sql);
		query.setParameter("atenderHoje", atenderHoje);
		query.setParameter("unidade", unidade);

		List<Object[]> linhas = query.getResultList();

		List<Integer> idbams = linhas.stream().map(row -> ((Number) row[9]).intValue()).toList();
		Map<Integer, AtendimentoMultidisciplinar> existentesPorIdbam = idbams.isEmpty() ? Map.of()
				: AtendimentoMultidisciplinar
						.<AtendimentoMultidisciplinar>list(
								"idbam in ?1 and ativo = true and status not in ('FINALIZADO', 'CANCELADO')", idbams)
						.stream()
						.collect(Collectors.toMap(AtendimentoMultidisciplinar::getIdbam, a -> a, (primeiro, ignorado) -> primeiro));

		Map<Integer, AtendimentoMultidisciplinarFilaDto> pacientesPorId = new java.util.LinkedHashMap<>();

		for (Object[] row : linhas) {
			AtendimentoMultidisciplinarFilaDto dto = new AtendimentoMultidisciplinarFilaDto();
			dto.setIdPaciente(row[0] != null ? ((Number) row[0]).intValue() : 0);
			dto.setNome((String) row[1]);
			dto.setIdade(row[2] != null ? ((Number) row[2]).intValue() : null);
			dto.setPacienteespecial((String) row[3]);
			dto.setPai((String) row[4]);
			dto.setMae((String) row[5]);
			dto.setNascimento((String) row[6]);
			dto.setIdAvaliacao(row[7] != null ? ((Number) row[7]).intValue() : 0);
			dto.setAvaliado(row[8] != null ? ((Number) row[8]).intValue() : 0);
			dto.setIdBam(row[9] != null ? ((Number) row[9]).intValue() : 0);
			dto.setHora((String) row[10]);
			dto.setCor(row[11] != null ? ((Number) row[11]).intValue() : 1);
			dto.setIdRegistro(row[12] != null ? ((Number) row[12]).intValue() : 0);
			dto.setSetor((String) row[13]);
			dto.setIdTratamento(row[14] != null ? ((Number) row[14]).intValue() : 0);
			dto.setTratado(row[15] != null ? ((Number) row[15]).intValue() : 0);
			dto.setCategoriaProfissional(atenderHoje);
			dto.setModalidade("");

			AtendimentoMultidisciplinar existente = existentesPorIdbam.get(dto.getIdBam());
			dto.setIdAtendimento(existente != null ? existente.getId() : null);
			dto.setStatusAtendimento(existente != null ? existente.getStatus() : "AGUARDANDO");
			resultado.add(dto);
			pacientesPorId.put(dto.getIdPaciente(), dto);
		}

		mesclarAgendados(resultado, pacientesPorId, atenderHoje, unidade);

		return resultado;
	}

	// Pacientes com agendamento para hoje neste setor mas que ainda nao chegaram
	// (sem BAM aberto) tambem aparecem na fila, marcados como agendados — mesmo
	// padrao de OdontologiaService.filaOdontologica (fila + agendados mesclados).
	@SuppressWarnings("unchecked")
	private void mesclarAgendados(List<AtendimentoMultidisciplinarFilaDto> resultado,
			Map<Integer, AtendimentoMultidisciplinarFilaDto> pacientesPorId, String atenderHoje, String unidade) {
		String sqlAgendados = """
			SELECT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento,
			       ep.horamarcada, ep.setor, COALESCE(ep.modalidade, 'PRESENCIAL'), ep.medico
			FROM escalapaciente ep
			JOIN pacientes p ON p.idpaciente = ep.paciente_idpaciente
			WHERE translate(UPPER(ep.setor), 'ÁÀÃÂÄÉÈÊËÍÌÎÏÓÒÕÔÖÚÙÛÜÇÑ', 'AAAAAEEEEIIIIOOOOOUUUUCN')
			        LIKE translate(UPPER(:atenderHoje), 'ÁÀÃÂÄÉÈÊËÍÌÎÏÓÒÕÔÖÚÙÛÜÇÑ', 'AAAAAEEEEIIIIOOOOOUUUUCN') || '%'
			  AND UPPER(ep.unidade) = UPPER(:unidade)
			  AND ep.agenda = true
			  AND ep.atendido = false
			  AND ep.inicioagenda = CURRENT_DATE
			ORDER BY ep.horamarcada
			""";

		var query = entityManager.createNativeQuery(sqlAgendados);
		query.setParameter("atenderHoje", atenderHoje);
		query.setParameter("unidade", unidade);

		for (Object[] row : (List<Object[]>) query.getResultList()) {
			int idPaciente = row[0] != null ? ((Number) row[0]).intValue() : 0;
			String modalidade = row[9] != null ? row[9].toString().trim().toUpperCase(java.util.Locale.ROOT) : "PRESENCIAL";
			modalidade = modalidade.contains("VIDEO") || modalidade.contains("VÍDEO") ? "VIDEO" : "PRESENCIAL";

			AtendimentoMultidisciplinarFilaDto existente = pacientesPorId.get(idPaciente);
			if (existente != null) {
				existente.setAgendado(true);
				existente.setModalidade(modalidade);
				existente.setMedicoAgendado(row[10] != null ? row[10].toString().trim() : "");
				continue;
			}

			AtendimentoMultidisciplinarFilaDto dto = new AtendimentoMultidisciplinarFilaDto();
			dto.setIdPaciente(idPaciente);
			dto.setNome((String) row[1]);
			dto.setIdade(row[2] != null ? ((Number) row[2]).intValue() : null);
			dto.setPacienteespecial((String) row[3]);
			dto.setMae((String) row[4]);
			dto.setPai((String) row[5]);
			dto.setNascimento((String) row[6]);
			dto.setHora((String) row[7]);
			dto.setSetor((String) row[8]);
			dto.setCategoriaProfissional(atenderHoje);
			dto.setAgendado(true);
			dto.setModalidade(modalidade);
			dto.setMedicoAgendado(row[10] != null ? row[10].toString().trim() : "");
			dto.setStatusAtendimento("AGUARDANDO");
			resultado.add(dto);
			pacientesPorId.put(idPaciente, dto);
		}
	}

	public List<AtendimentoMultidisciplinar> listarPorPaciente(int idpaciente) {
		return repository.listarPorPaciente(idpaciente);
	}

	public List<AtendimentoMultidisciplinar> listarPorIdbam(int idbam) {
		return repository.listarPorIdbam(idbam);
	}

	public AtendimentoMultidisciplinar buscarPorId(int id) {
		return AtendimentoMultidisciplinar.findById(id);
	}

	@Transactional
	public AtendimentoMultidisciplinar salvar(AtendimentoMultidisciplinarDto dto, AuthenticatedUser usuario) {
		if (dto.getCategoriaProfissional() == null || dto.getCategoriaProfissional().isBlank()) {
			throw new IllegalArgumentException("Categoria profissional e obrigatoria.");
		}
		AtendimentoMultidisciplinar atendimento = new AtendimentoMultidisciplinar();
		copiarDtoParaEntity(dto, atendimento);
		atendimento.setDataAtendimento(new Date());
		atendimento.setStatus("AGUARDANDO");
		atendimento.setAtivo(true);
		atendimento.setProfissional(usuario == null ? dto.getProfissional() : usuario.nome());
		atendimento.setMatriculaProfissional(usuario == null ? "" : usuario.matricula());
		atendimento.setCriadoPorMatricula(usuario == null ? "" : usuario.matricula());
		atendimento.persist();
		return atendimento;
	}

	@Transactional
	public void atualizar(int id, AtendimentoMultidisciplinarDto dto, AuthenticatedUser usuario) {
		AtendimentoMultidisciplinar atendimento = buscarAtivoOuFalhar(id);
		validarNaoFinalizado(atendimento);
		copiarDtoParaEntity(dto, atendimento);
		atendimento.setAtualizadoPorMatricula(usuario == null ? "" : usuario.matricula());
		atendimento.persist();
	}

	@Transactional
	public void iniciar(int id, AuthenticatedUser usuario) {
		AtendimentoMultidisciplinar atendimento = buscarAtivoOuFalhar(id);
		atendimento.setStatus("EM_ATENDIMENTO");
		atendimento.setAtualizadoPorMatricula(usuario == null ? "" : usuario.matricula());
		atendimento.persist();
	}

	@Transactional
	public void finalizar(int id, AtendimentoMultidisciplinarDto dto, AuthenticatedUser usuario) {
		AtendimentoMultidisciplinar atendimento = buscarAtivoOuFalhar(id);
		validarNaoFinalizado(atendimento);
		if (dto.getEvolucao() == null || dto.getEvolucao().isBlank()) {
			throw new IllegalArgumentException("Evolucao e obrigatoria para finalizar o atendimento.");
		}
		if (dto.getConteudoHash() == null || dto.getConteudoHash().isBlank()) {
			throw new IllegalArgumentException("Assinatura e obrigatoria para finalizar o atendimento.");
		}
		copiarDtoParaEntity(dto, atendimento);
		atendimento.setStatus("FINALIZADO");
		atendimento.setAtendimentoConcluido(true);
		atendimento.setDataEncerramento(new Date());
		atendimento.setAtualizadoPorMatricula(usuario == null ? "" : usuario.matricula());
		atendimento.persist();

		AssinaturaClinicaDto assinatura = new AssinaturaClinicaDto();
		assinatura.setTipoDocumento(AssinaturaClinicaService.ATENDIMENTO_MULTIDISCIPLINAR);
		assinatura.setIddocumento(atendimento.getId());
		assinatura.setIdbam(atendimento.getIdbam());
		assinatura.setIdpaciente(atendimento.getIdpaciente());
		assinatura.setUnidade(atendimento.getUnidade());
		assinatura.setConteudoHash(dto.getConteudoHash());
		assinatura.setConteudoResumo(dto.getConteudoResumo());
		assinaturaClinicaService.assinar(assinatura, usuario);

		fecharBam(atendimento.getIdbam());
	}

	// Mesmo mecanismo generico usado em outras finalizacoes do sistema
	// (TratamentoRepository.finalizarBam): fecha o tratamento do BAM para que o
	// paciente saia de todas as filas que checam tratamento.tratado = 0.
	private void fecharBam(Integer idbam) {
		if (idbam == null) {
			return;
		}
		entityManager.createNativeQuery(
				"UPDATE tratamento SET tratado = 1 WHERE idtratamento = "
						+ "(SELECT tratamento_idtratamento FROM bam WHERE idbam = :idbam)")
				.setParameter("idbam", idbam)
				.executeUpdate();
	}

	@Transactional
	public void cancelar(int id, String motivoCancelamento, AuthenticatedUser usuario) {
		if (motivoCancelamento == null || motivoCancelamento.isBlank()) {
			throw new IllegalArgumentException("Informe a justificativa do cancelamento.");
		}
		AtendimentoMultidisciplinar atendimento = buscarAtivoOuFalhar(id);
		validarNaoFinalizado(atendimento);
		atendimento.setStatus("CANCELADO");
		atendimento.setMotivoCancelamento(motivoCancelamento);
		atendimento.setAtualizadoPorMatricula(usuario == null ? "" : usuario.matricula());
		atendimento.persist();
	}

	@Transactional
	public void reabrir(int id, AuthenticatedUser usuario) {
		AtendimentoMultidisciplinar atendimento = buscarAtivoOuFalhar(id);
		String matricula = usuario == null ? "" : usuario.matricula();
		boolean mesmoDia = mesmoDia(atendimento.getCriadoEm());
		boolean mesmoAutor = matricula.equals(atendimento.getCriadoPorMatricula());
		boolean admin = usuario != null && usuario.hasAnyPermission(List.of("kadm"));
		if (!admin && !(mesmoAutor && mesmoDia)) {
			throw new ForbiddenException(
					"Apenas quem criou o atendimento (no mesmo dia) ou um administrador pode reabri-lo.");
		}
		atendimento.setStatus("EM_ATENDIMENTO");
		atendimento.setDataEncerramento(null);
		atendimento.setAtualizadoPorMatricula(matricula);
		atendimento.persist();
	}

	private AtendimentoMultidisciplinar buscarAtivoOuFalhar(int id) {
		Optional<AtendimentoMultidisciplinar> op = AtendimentoMultidisciplinar.findByIdOptional(id);
		AtendimentoMultidisciplinar atendimento = op
				.orElseThrow(() -> new NullPointerException("Atendimento multidisciplinar nao encontrado."));
		if (!atendimento.isAtivo()) {
			throw new IllegalArgumentException("Este atendimento foi excluido/inativado.");
		}
		return atendimento;
	}

	private void validarNaoFinalizado(AtendimentoMultidisciplinar atendimento) {
		if ("FINALIZADO".equals(atendimento.getStatus())) {
			throw new IllegalArgumentException(
					"Este atendimento ja foi finalizado. Registre uma retificacao/nova evolucao em vez de editar.");
		}
	}

	private boolean mesmoDia(Date data) {
		if (data == null) {
			return false;
		}
		LocalDate dia = data.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return dia.equals(LocalDate.now());
	}

	private void copiarDtoParaEntity(AtendimentoMultidisciplinarDto dto, AtendimentoMultidisciplinar a) {
		a.setIdpaciente(dto.getIdpaciente());
		a.setIdbam(dto.getIdbam());
		a.setProntuario(dto.getProntuario());
		a.setUnidade(dto.getUnidade());
		a.setSetor(dto.getSetor());
		a.setLeito(dto.getLeito());

		a.setCategoriaProfissional(dto.getCategoriaProfissional());
		a.setConselho(dto.getConselho());
		a.setNumeroConselho(dto.getNumeroConselho());

		a.setHoraInicial(dto.getHoraInicial());
		a.setHoraFinal(dto.getHoraFinal());
		a.setMotivo(dto.getMotivo());
		a.setOrigem(dto.getOrigem());
		a.setSolicitante(dto.getSolicitante());
		a.setPrioridade(dto.getPrioridade());
		a.setTipoAtendimento(dto.getTipoAtendimento());
		a.setAcompanhantePresente(dto.getAcompanhantePresente());
		a.setObservacoesIniciais(dto.getObservacoesIniciais());

		a.setQueixaPrincipal(dto.getQueixaPrincipal());
		a.setHistoriaAtual(dto.getHistoriaAtual());
		a.setCondicaoClinicaObservada(dto.getCondicaoClinicaObservada());
		a.setNivelConsciencia(dto.getNivelConsciencia());
		a.setOrientacao(dto.getOrientacao());
		a.setComunicacao(dto.getComunicacao());
		a.setMobilidade(dto.getMobilidade());
		a.setAutonomia(dto.getAutonomia());
		a.setDor(dto.getDor());
		a.setAlimentacaoAvaliacao(dto.getAlimentacaoAvaliacao());
		a.setDegluticao(dto.getDegluticao());
		a.setRespiracao(dto.getRespiracao());
		a.setEliminacoes(dto.getEliminacoes());
		a.setCondicoesEmocionais(dto.getCondicoesEmocionais());
		a.setCondicoesFamiliares(dto.getCondicoesFamiliares());
		a.setRedeApoio(dto.getRedeApoio());
		a.setVulnerabilidadeSocial(dto.getVulnerabilidadeSocial());
		a.setAdesaoTratamento(dto.getAdesaoTratamento());
		a.setRiscosIdentificados(dto.getRiscosIdentificados());
		a.setNecessidadesIdentificadas(dto.getNecessidadesIdentificadas());
		a.setObservacoesGerais(dto.getObservacoesGerais());

		a.setDadosEspecificos(dto.getDadosEspecificos());

		a.setObjetivos(dto.getObjetivos());
		a.setCondutas(dto.getCondutas());
		a.setIntervencoes(dto.getIntervencoes());
		a.setOrientacoes(dto.getOrientacoes());
		a.setEncaminhamentosTexto(dto.getEncaminhamentosTexto());
		a.setSolicitacoes(dto.getSolicitacoes());
		a.setMetas(dto.getMetas());
		a.setPrazoEstimado(dto.getPrazoEstimado());
		a.setNecessitaRetorno(dto.getNecessitaRetorno());
		if (dto.getDataPrevistaReavaliacao() != null && !dto.getDataPrevistaReavaliacao().isBlank()) {
			a.setDataPrevistaReavaliacao(java.sql.Date.valueOf(dto.getDataPrevistaReavaliacao().substring(0, 10)));
		}

		a.setEvolucao(dto.getEvolucao());
		a.setRespostaIntervencoes(dto.getRespostaIntervencoes());
		a.setIntercorrencias(dto.getIntercorrencias());
		a.setAlteracaoConduta(dto.getAlteracaoConduta());
		a.setOrientacoesFornecidas(dto.getOrientacoesFornecidas());
		a.setContatoFamiliares(dto.getContatoFamiliares());
		a.setContatoOutrosProfissionais(dto.getContatoOutrosProfissionais());
		a.setResultadoAtendimento(dto.getResultadoAtendimento());

		a.setAvaliacaoIa(dto.getAvaliacaoIa());
		a.setCondicaoTermino(dto.getCondicaoTermino());
		a.setNecessitaAcompanhamento(dto.getNecessitaAcompanhamento());
		a.setNecessitaRetornoFinal(dto.getNecessitaRetornoFinal());
		a.setEncaminhamentoRealizado(dto.getEncaminhamentoRealizado());
		a.setTransferenciaSetor(dto.getTransferenciaSetor());
		a.setTransferenciaUnidade(dto.getTransferenciaUnidade());
		a.setAltaMultidisciplinar(dto.getAltaMultidisciplinar());
		a.setObservacoesFinais(dto.getObservacoesFinais());
	}

}
