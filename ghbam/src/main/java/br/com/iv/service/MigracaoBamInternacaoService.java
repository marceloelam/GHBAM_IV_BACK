package br.com.iv.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Leva a internacao aberta, o leito do censo e todos os registros clinicos de um BAM
 * antigo para o ultimo BAM do paciente (tambem com cadastro duplicado, mesmo CPF).
 * Pode ser repetida: se a internacao ja estiver no destino, so leva o que sobrou.
 * Nao apaga nenhum BAM.
 */
@ApplicationScoped
public class MigracaoBamInternacaoService {

	// Tudo o que e do atendimento clinico. Ficam de fora: identidade do BAM (bam, atendimento,
	// unidadeatendido), internacao/novointernado (tratados no metodo), assinatura_clinica,
	// envios externos (fhir_envio_fila, notificacao_sinan), contas geradas (faturamento_aih,
	// faturamento_apac, tiss_conta) e auditoria/log. Formato: tabela, coluna, coluna e texto.
	private static final Object[][] TABELAS_DO_BAM = {
		{ "abrirprontuario", "nbam", false }, { "afaturar", "bam", false }, { "afaturar2", "bam", false },
		{ "altaadmnir", "bam", false }, { "altacomplexidade", "bam", false }, { "anamneseinternacao", "nbam", false },
		{ "anotacao_enfermagem", "idbam", false }, { "assistenciafarma", "nbam", true },
		{ "atendimento_multidisciplinar", "idbam", false }, { "atendimentomultidisciplinar", "idbam", false },
		{ "balanco_hidrico_anotacoes_enfermagem", "bam", true }, { "balanco_hidrico_entradas", "bam", true },
		{ "balanco_hidrico_pendencias", "bam", true }, { "balanco_hidrico_plantoes", "bam", true },
		{ "balanco_hidrico_saidas", "bam", true }, { "balanco_hidrico_transportes_saldo", "bam", true },
		{ "balancohidrico", "bam", false }, { "cirurgia_agendamento", "idbam", false },
		{ "classificacao_ghbam_plano_execucao", "bam", false }, { "classificacao_ghbam_plano_item", "bam", false },
		{ "conformidade_pendencia_baixa", "idbam", false }, { "consulta_pediatrica", "idbam", false },
		{ "consulta_pediatrica", "nbam", false }, { "consultapediatrica", "idbam", false },
		{ "consultapediatrica", "nbam", false }, { "controle_assistencial_registro", "idbam", false },
		{ "documento_assistencial", "idbam", false }, { "escaladeenfermagem", "bam", true },
		{ "evolucaopaciente", "nbam", false }, { "evolucaopacienteenf", "nbam", false },
		{ "evolucaopacientereceituario", "nbam", false }, { "examescomplementares", "nbam", false },
		{ "ged_documento", "idbam", false }, { "imagens", "idbam", false }, { "infocomplemetares", "bam", true },
		{ "laboratorio", "idbam", false }, { "laudoautorizacao", "bam", false }, { "laudorecurso", "nbam", false },
		{ "laudosolautoproambu", "nbam", false }, { "listaanestesica", "bam", false }, { "medicacao", "bam", false },
		{ "novointernadoadm", "bam_idbam", false }, { "novointernadoadm", "nbam", false }, { "nsp_evento", "idbam", false },
		{ "odontologia_atendimento", "idbam", false }, { "parecerespecialista", "nbam", false },
		{ "pedidomedico", "bam", false }, { "pedidomedico2", "bam", false }, { "pedidoresultadolaboratorio", "bam", false },
		{ "pep_anotacao_rascunho", "idbam", false }, { "pharmacia_validacao", "idbam", false }, { "prescricao", "bam", false },
		{ "prescricao_aprazamento_checagem", "idbam", false }, { "prescricao_item_ciclo_evento", "idbam", false },
		{ "prescricao_item_estruturado", "idbam", false }, { "prescricaoassist", "bam", false },
		{ "prescricaocont", "bam", false }, { "procedimentos", "idbam", false }, { "prontuario_clinico_item", "idbam", false },
		{ "protocolos", "idbam", false }, { "protocolosinan", "numerodobam", false }, { "receitamedicamentos", "nbam", false },
		{ "resumo_balanco_hidrico", "bam", true }, { "resumodealta", "bam", true }, { "riscocirurgico", "nbam", false },
		{ "sae_enfermagem", "idbam", false }, { "sumarioobito", "bam", true }, { "violencia", "bam", false },
		{ "visitantes", "nbam", true },
	};

	@Inject
	EntityManager entityManager;

	@Inject
	ProntuarioDefinitivoService prontuarioDefinitivoService;

	@Transactional
	public Map<String, Object> migrar(int bamOrigem, int bamDestino, String responsavel) {
		Object[] origem = dadosBam(bamOrigem);
		Object[] destino = dadosBam(bamDestino);
		if (origem == null || destino == null) {
			throw recusa("BAM de origem ou de destino não encontrado.");
		}
		int pacOrigem = ((Number) origem[0]).intValue();
		int pacDestino = ((Number) destino[0]).intValue();
		String unidade = (String) destino[1];
		if (unidade == null || !unidade.equals(origem[1])) {
			throw recusa("Os BAMs são de unidades diferentes.");
		}
		String cpfOrigem = (String) origem[2];
		boolean mesmoCpf = cpfOrigem != null && cpfOrigem.length() == 11 && cpfOrigem.equals(destino[2]);
		// RN e cadastros sem CPF: o mesmo prontuário também identifica a pessoa.
		// Exige formato válido e mesmo nascimento: há prontuários-lixo ('-', '10') repetidos.
		String prontOrigem = (String) origem[3];
		boolean mesmoProntuario = prontOrigem != null && prontOrigem.matches("[0-9]+-[0-9]+")
				&& prontOrigem.equals(destino[3]) && origem[4] != null && origem[4].equals(destino[4]);
		if (pacOrigem != pacDestino && !mesmoCpf && !mesmoProntuario) {
			throw recusa("Os BAMs não são do mesmo paciente (nem do mesmo CPF ou prontuário).");
		}
		if (bamDestino <= bamOrigem) {
			throw recusa("O BAM de destino precisa ser mais recente que o de origem.");
		}
		Number maisRecente = (Number) entityManager.createNativeQuery(
				"SELECT MAX(idbam) FROM bam WHERE paciente_idpaciente = :p AND unidade = :u")
				.setParameter("p", pacDestino).setParameter("u", unidade).getSingleResult();
		if (maisRecente != null && maisRecente.intValue() != bamDestino) {
			throw recusa("O BAM de destino não é o último BAM do paciente (último: " + maisRecente + ").");
		}

		// Vale só a AIH do BAM mais recente: se ele já tem, a AIH antiga fica no BAM antigo.
		boolean destinoTemAih = contar("SELECT count(*) FROM laudoautorizacao WHERE bam = :b", bamDestino) > 0;

		String abertaSql ="SELECT count(*) FROM internacao WHERE bam = :b AND datasaida IS NULL AND motivodaalta = '-'";
		long abertaOrigem = contar(abertaSql, String.valueOf(bamOrigem));
		long abertaDestino = contar(abertaSql, String.valueOf(bamDestino));
		// Repetição: internação já no destino -> só leva o que ficou para trás no BAM antigo.
		boolean repeticao = abertaOrigem == 0 && abertaDestino == 1;
		if (!repeticao) {
			if (abertaOrigem != 1) {
				throw recusa("O BAM de origem não tem uma internação aberta para migrar.");
			}
			if (contar("SELECT count(*) FROM internacao WHERE bam = :b", String.valueOf(bamDestino)) > 0) {
				throw recusa("O BAM de destino já tem internação registrada.");
			}
		}

		// RN internado na Admissao do RN chega aqui com o prontuario temporario: migra ja com o definitivo.
		String prontuario = prontuarioDefinitivoService.garantir(pacDestino, responsavel);
		if (prontuario == null) {
			prontuario = (String) destino[3];
		}

		int internacoes = 0;
		int censo = 0;
		if (!repeticao) {
			internacoes = entityManager.createNativeQuery(
					"UPDATE internacao SET bam = :novo, iddopaciente = :p, prontuario = :pront "
							+ "WHERE bam = :antigo AND datasaida IS NULL AND motivodaalta = '-'")
					.setParameter("novo", String.valueOf(bamDestino))
					.setParameter("p", pacDestino)
					.setParameter("pront", prontuario)
					.setParameter("antigo", String.valueOf(bamOrigem))
					.executeUpdate();
		}
		// Leito: só move se o destino ainda não estiver no censo.
		if (contar("SELECT count(*) FROM novointernado WHERE nbam = :b AND alta = false", bamDestino) == 0) {
			censo = entityManager.createNativeQuery(
					"UPDATE novointernado SET nbam = :novo, bam_idbam = :novo, pacientes_idpaciente = :p, prontuario = :pront "
							+ "WHERE nbam = :antigo AND alta = false")
					.setParameter("novo", bamDestino)
					.setParameter("p", pacDestino)
					.setParameter("pront", prontuario)
					.setParameter("antigo", bamOrigem)
					.executeUpdate();
		}
		Map<String, Integer> registros = moverRegistros(bamOrigem, bamDestino, destinoTemAih);

		Log.infof("Migracao de internacao: BAM %d -> BAM %d (internacao=%d, censo=%d, registros=%s, repeticao=%s) por %s",
				bamOrigem, bamDestino, internacoes, censo, registros, repeticao, responsavel);
		Map<String, Object> resultado = new LinkedHashMap<>();
		resultado.put("bamOrigem", bamOrigem);
		resultado.put("bamDestino", bamDestino);
		resultado.put("internacao", internacoes);
		resultado.put("censo", censo);
		resultado.put("registros", registros);
		return resultado;
	}

	private Map<String, Integer> moverRegistros(int bamOrigem, int bamDestino, boolean destinoTemAih) {
		Map<String, Integer> movidos = new LinkedHashMap<>();
		for (Object[] t : TABELAS_DO_BAM) {
			String tabela = (String) t[0];
			String coluna = (String) t[1];
			if (destinoTemAih && "laudoautorizacao".equals(tabela)) {
				continue;
			}
			boolean texto = (Boolean) t[2];
			// Banco em versão anterior (tabela ainda não criada): pula, sem abortar a transação.
			if (entityManager.createNativeQuery("SELECT cast(to_regclass(:t) as text)").setParameter("t", "public." + tabela)
					.getSingleResult() == null) {
				continue;
			}
			int n = entityManager.createNativeQuery(
					"UPDATE " + tabela + " SET " + coluna + " = :novo WHERE " + coluna + " = :antigo")
					.setParameter("novo", texto ? (Object) String.valueOf(bamDestino) : (Object) bamDestino)
					.setParameter("antigo", texto ? (Object) String.valueOf(bamOrigem) : (Object) bamOrigem)
					.executeUpdate();
			if (n > 0) {
				movidos.put(tabela + "." + coluna, n);
			}
		}
		return movidos;
	}

	@SuppressWarnings("unchecked")
	private Object[] dadosBam(int idbam) {
		List<Object[]> linhas = entityManager.createNativeQuery(
				"SELECT b.paciente_idpaciente, b.unidade, regexp_replace(coalesce(p.cpf, ''), '[^0-9]', '', 'g'), p.prontuario, "
						+ "cast(p.nascimento as text) "
						+ "FROM bam b JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente WHERE b.idbam = :b")
				.setParameter("b", idbam).getResultList();
		return linhas.isEmpty() ? null : linhas.get(0);
	}

	private long contar(String sql, Object bam) {
		return ((Number) entityManager.createNativeQuery(sql).setParameter("b", bam).getSingleResult()).longValue();
	}

	private static WebApplicationException recusa(String mensagem) {
		return new WebApplicationException(Response.status(Response.Status.CONFLICT)
				.entity(Map.of("erro", mensagem)).build());
	}
}
