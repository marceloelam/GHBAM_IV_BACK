package br.com.iv.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.entity.PedidoResultadoLaboratorioItem;

/**
 * Portal publico do paciente: com CPF + nascimento devolve os dados de identificacao e os
 * laudos de laboratorio ja liberados (com PDF), de todos os cadastros com aquele par (o
 * cadastro tem CPFs duplicados). O download exige o token curto da consulta e so aceita
 * pedidos que vieram nela. O laudo pode estar no MinIO ou no banco: quem le e o backend.
 */
@ApplicationScoped
public class PortalPacienteService {

	private static final Logger LOG = Logger.getLogger(PortalPacienteService.class);
	// Abaixo disso nao e laudo em PDF (mesmo corte do PedidoResultadoLaboratorioService).
	private static final int TAMANHO_MINIMO_PDF = 600;
	private static final long VALIDADE_TOKEN_SEGUNDOS = 900; // 15 minutos
	private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Inject
	EntityManager em;

	@Inject
	ArmazenamentoArquivoService armazenamento;

	private final SecureRandom aleatorio = new SecureRandom();
	private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();

	private record Sessao(Set<Integer> pedidos, long expiraEpoch) {
	}

	public record Exame(int id, String descricao, String data, String hora, String unidade) {
	}

	public record Consulta(String token, String nome, Integer idade, String nascimento, String endereco,
			List<Exame> exames) {
	}

	public record Laudo(byte[] conteudo, String tipo) {
	}

	/** null quando o par CPF + nascimento nao confere ou nao ha laudo liberado. */
	public Consulta consultar(String cpf, LocalDate nascimento) {
		String nascimentoTexto = nascimento.format(DD_MM_AAAA);
		@SuppressWarnings("unchecked")
		List<Object[]> cadastros = em.createNativeQuery("""
				select idpaciente, nome, nascimento, endereco, numero, bairro, municipio, estado, cep
				from pacientes
				where regexp_replace(coalesce(cpf, ''), '\\D', '', 'g') = :cpf
				order by idpaciente desc
				""").setParameter("cpf", cpf).getResultList();
		List<Object[]> conferem = cadastros.stream()
				.filter(c -> nascimentoTexto.equals(ConsultaCnsService.formatarData(texto(c[2]))))
				.toList();
		if (conferem.isEmpty()) return null;

		List<Integer> ids = conferem.stream().map(c -> ((Number) c[0]).intValue()).toList();
		@SuppressWarnings("unchecked")
		List<Object[]> linhas = em.createNativeQuery("""
				select p.idpedidoresultado, p.descricao, p.dt, p.hr, p.unidade
				from pedidoresultadolaboratorio p
				inner join bam b on b.idbam = p.bam
				where b.paciente_idpaciente in (:ids)
				  and p.local = 'LABORATORIO'
				  and (coalesce(p.armazenamento_chave, '') <> '' or octet_length(p.arquivo) >= :minimo)
				""").setParameter("ids", ids).setParameter("minimo", TAMANHO_MINIMO_PDF).getResultList();
		if (linhas.isEmpty()) return null;

		Map<String, String> nomesUnidade = new HashMap<>();
		List<Exame> exames = new ArrayList<>();
		for (Object[] l : linhas) {
			int id = ((Number) l[0]).intValue();
			String data = ConsultaCnsService.formatarData(texto(l[2]));
			String sigla = texto(l[4]).trim();
			exames.add(new Exame(id, descricao(id, texto(l[1])), data == null ? texto(l[2]) : data, texto(l[3]).trim(),
					nomesUnidade.computeIfAbsent(sigla.toUpperCase(), s -> nomeUnidade(sigla))));
		}
		// Mais recente primeiro; a data vem como texto, entao ordena pela data de fato.
		exames.sort(Comparator.comparing((Exame e) -> dataOrdenavel(e.data())).thenComparing(Exame::hora).reversed());

		Object[] cadastro = conferem.get(0);
		String token = novoToken(exames.stream().map(Exame::id).collect(Collectors.toSet()));
		return new Consulta(token, texto(cadastro[1]).trim(), idade(nascimento), nascimentoTexto, endereco(cadastro), exames);
	}

	/** null quando o token expirou ou o pedido nao veio na consulta deste token. */
	public Laudo laudo(String token, int idPedido) {
		Sessao sessao = token == null ? null : sessoes.get(token);
		if (sessao == null || sessao.expiraEpoch() < Instant.now().getEpochSecond() || !sessao.pedidos().contains(idPedido)) {
			return null;
		}
		PedidoResultadoLaboratorio pedido = PedidoResultadoLaboratorio.findById(idPedido);
		if (pedido == null) return null;
		String chave = pedido.getArmazenamentoChave();
		String bruto = chave != null && !chave.isBlank()
				? new String(armazenamento.ler(chave), StandardCharsets.UTF_8)
				: pedido.getArquivo();
		if (bruto == null || bruto.isBlank()) return null;
		byte[] conteudo;
		try {
			conteudo = Base64.getMimeDecoder().decode(bruto.trim().replaceFirst("^data:[^,]*,", ""));
		} catch (IllegalArgumentException falha) {
			LOG.warnf("Portal paciente: laudo do pedido %d nao esta em Base64", idPedido);
			return null;
		}
		return new Laudo(conteudo, tipo(conteudo));
	}

	private String descricao(int idPedido, String descricao) {
		if (!descricao.isBlank()) return descricao.trim();
		String itens = PedidoResultadoLaboratorioItem.doResultado(idPedido).stream()
				.map(PedidoResultadoLaboratorioItem::getNomeExame).distinct().collect(Collectors.joining(", "));
		return itens.isBlank() ? "Exame laboratorial" : itens;
	}

	private String nomeUnidade(String sigla) {
		if (sigla.isEmpty()) return "";
		List<String> nomes = em.createQuery(
				"select c.hospital from configurarunidade c where upper(c.sigla) = upper(:sigla)", String.class)
				.setParameter("sigla", sigla).setMaxResults(1).getResultList();
		String nome = nomes.isEmpty() || nomes.get(0) == null ? "" : nomes.get(0).trim();
		return nome.isEmpty() ? sigla : nome;
	}

	private String novoToken(Set<Integer> pedidos) {
		long agora = Instant.now().getEpochSecond();
		sessoes.entrySet().removeIf(e -> e.getValue().expiraEpoch() < agora);
		byte[] bytes = new byte[32];
		aleatorio.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		sessoes.put(token, new Sessao(Set.copyOf(pedidos), agora + VALIDADE_TOKEN_SEGUNDOS));
		return token;
	}

	private static String endereco(Object[] c) {
		Map<String, String> partes = new LinkedHashMap<>();
		String rua = texto(c[3]).trim();
		String numero = texto(c[4]).trim();
		partes.put("rua", numero.isEmpty() ? rua : rua + ", " + numero);
		partes.put("bairro", texto(c[5]).trim());
		String municipio = texto(c[6]).trim();
		String estado = texto(c[7]).trim();
		partes.put("cidade", estado.isEmpty() ? municipio : municipio.isEmpty() ? estado : municipio + " - " + estado);
		String cep = texto(c[8]).trim();
		partes.put("cep", cep.isEmpty() ? "" : "CEP " + cep);
		return partes.values().stream().filter(p -> !p.isBlank()).collect(Collectors.joining(", "));
	}

	private static Integer idade(LocalDate nascimento) {
		LocalDate hoje = LocalDate.now();
		return nascimento.isAfter(hoje) ? null : Period.between(nascimento, hoje).getYears();
	}

	private static String dataOrdenavel(String data) {
		try {
			return LocalDate.parse(data, DD_MM_AAAA).toString();
		} catch (RuntimeException naoEData) {
			return "";
		}
	}

	private static String tipo(byte[] conteudo) {
		if (conteudo.length > 3 && conteudo[0] == (byte) 0xFF && conteudo[1] == (byte) 0xD8) return "image/jpeg";
		if (conteudo.length > 4 && conteudo[0] == (byte) 0x89 && conteudo[1] == 'P' && conteudo[2] == 'N' && conteudo[3] == 'G') return "image/png";
		return "application/pdf";
	}

	private static String texto(Object valor) {
		return valor == null ? "" : valor.toString();
	}
}
