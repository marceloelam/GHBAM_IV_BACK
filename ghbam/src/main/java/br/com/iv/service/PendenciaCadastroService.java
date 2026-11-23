package br.com.iv.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.entity.Servidor;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

/**
 * Pendência de cadastro profissional (V264). Não trava cadastro nem login (decisão 22/11/2026):
 * abre 30 dias para regularizar; vencida, o alerta escala para coordenador/administrativo/super
 * e os documentos deixam de imprimir o número do conselho.
 */
@ApplicationScoped
public class PendenciaCadastroService {

	private static final Logger LOG = Logger.getLogger(PendenciaCadastroService.class);
	public static final int PRAZO_DIAS = 30;

	/** Mesma normalização usada na comparação: sem sufixo após "-", pontuação e zeros à esquerda. */
	static final String SQL_NORMALIZA = "regexp_replace(regexp_replace(regexp_replace(upper(trim(coalesce(%s, ''))), '-.*$', ''), '[^0-9A-Z]', '', 'g'), '^0+', '')";

	@Inject
	EntityManager em;

	private volatile boolean tabelaPronta;

	static String normalizarRegistro(String registro) {
		if (registro == null) {
			return "";
		}
		String texto = registro.trim().toUpperCase();
		int hifen = texto.indexOf('-');
		if (hifen > 0) {
			texto = texto.substring(0, hifen);
		}
		return texto.replaceAll("[^0-9A-Z]", "").replaceFirst("^0+", "");
	}

	private static String digitos(Object valor) {
		return valor == null ? "" : valor.toString().replaceAll("\\D", "");
	}

	private static String texto(Object valor) {
		return valor == null ? "" : valor.toString().trim();
	}

	static boolean ehEnfermagem(String cargo) {
		String c = cargo == null ? "" : cargo.trim().toUpperCase();
		return c.contains("ENFERMEIR") || c.startsWith("TEC") || c.startsWith("TÉC")
				|| c.contains("TECNIC") || c.contains("TÉCNIC") || c.contains("ENFERMAGEM");
	}

	private static String registroDe(Servidor s) {
		return normalizarRegistro(texto(s.getCorem()).isEmpty() ? s.getCrm() : s.getCorem());
	}

	private boolean tabelaPronta() {
		if (!tabelaPronta) {
			tabelaPronta = Boolean.TRUE.equals(em.createNativeQuery(
					"SELECT to_regclass('servidor_pendencia_cadastro') IS NOT NULL").getSingleResult());
		}
		return tabelaPronta;
	}

	/** Outras pessoas ativas (nome diferente e sem o mesmo CPF) com o mesmo número de conselho: [idservidor, nome, matricula, cpf]. */
	@SuppressWarnings("unchecked")
	List<Object[]> outrosComMesmoRegistro(Servidor s, String registro) {
		List<Object[]> linhas = em.createNativeQuery(
				"SELECT sv.idservidor, sv.nome, sv.matricula, sv.cpf FROM public.servidor sv "
				+ "WHERE sv.idservidor <> :id AND coalesce(sv.ativo, true) AND :reg IN ("
				+ String.format(SQL_NORMALIZA, "sv.corem") + ", " + String.format(SQL_NORMALIZA, "sv.crm") + ")")
			.setParameter("id", s.getIdservidor())
			.setParameter("reg", registro)
			.getResultList();
		String nome = texto(s.getNome()).toUpperCase();
		String cpf = digitos(s.getCpf());
		List<Object[]> outros = new ArrayList<>();
		for (Object[] l : linhas) {
			boolean mesmaPessoa = texto(l[1]).toUpperCase().equals(nome)
					|| (!cpf.isEmpty() && cpf.equals(digitos(l[3])));
			if (!mesmaPessoa) {
				outros.add(l);
			}
		}
		return outros;
	}

	/** Motivos da pendência do cadastro (vazio = regular). */
	public String avaliar(Servidor s) {
		if (Boolean.FALSE.equals(s.getAtivo())) {
			return "";
		}
		List<String> motivos = new ArrayList<>();
		String registro = registroDe(s);
		if (registro.isEmpty()) {
			if (ehEnfermagem(s.getCargo())) {
				motivos.add("Profissional de enfermagem sem COREN no cadastro.");
			}
		} else {
			for (Object[] outro : outrosComMesmoRegistro(s, registro)) {
				motivos.add("Registro " + registro + " também está no cadastro de " + texto(outro[1])
						+ " (matrícula " + texto(outro[2]) + ").");
			}
		}
		return String.join(" ", motivos);
	}

	/**
	 * Abre, atualiza ou resolve a pendência do servidor. Em conflito de registro, marca também
	 * o outro cadastro: não dá para saber qual dos dois está errado. Nunca impede salvar.
	 */
	public void sincronizar(Servidor s, String autor) {
		if (s == null || s.getIdservidor() == 0) {
			return;
		}
		try {
			if (!tabelaPronta()) {
				return;
			}
			aplicar(s, avaliar(s), autor);
			String registro = registroDe(s);
			if (!registro.isEmpty()) {
				for (Object[] outro : outrosComMesmoRegistro(s, registro)) {
					Servidor o = em.find(Servidor.class, ((Number) outro[0]).intValue());
					if (o != null) {
						aplicar(o, avaliar(o), autor);
					}
				}
			}
		} catch (RuntimeException e) {
			LOG.warnf("Pendencia de cadastro nao sincronizada para servidor %d: %s", s.getIdservidor(), e.getMessage());
		}
	}

	private void aplicar(Servidor s, String motivo, String autor) {
		if (motivo == null || motivo.isBlank()) {
			int resolvidas = em.createNativeQuery(
					"UPDATE servidor_pendencia_cadastro SET resolvida_em = now(), resolvida_por = :autor "
					+ "WHERE idservidor = :id AND resolvida_em IS NULL")
				.setParameter("autor", autor)
				.setParameter("id", s.getIdservidor())
				.executeUpdate();
			if (resolvidas > 0) {
				LOG.infof("[PENDENCIA-CADASTRO] resolvida servidor=%s por=%s", s.getMatricula(), autor);
				LogSistemaRegistro.registrar(em, s.getUnidade(), "Cadastro profissional regularizado: pendência encerrada de "
						+ texto(s.getNome()) + " (matrícula " + texto(s.getMatricula()) + ")");
			}
			return;
		}
		int atualizadas = em.createNativeQuery(
				"UPDATE servidor_pendencia_cadastro SET motivo = :motivo, unidade = :unidade "
				+ "WHERE idservidor = :id AND resolvida_em IS NULL")
			.setParameter("motivo", motivo)
			.setParameter("unidade", s.getUnidade())
			.setParameter("id", s.getIdservidor())
			.executeUpdate();
		if (atualizadas == 0) {
			em.createNativeQuery(
					"INSERT INTO servidor_pendencia_cadastro (idservidor, matricula, unidade, motivo, prazo) "
					+ "VALUES (:id, :matricula, :unidade, :motivo, current_date + " + PRAZO_DIAS + ")")
				.setParameter("id", s.getIdservidor())
				.setParameter("matricula", s.getMatricula())
				.setParameter("unidade", s.getUnidade())
				.setParameter("motivo", motivo)
				.executeUpdate();
			LOG.infof("[PENDENCIA-CADASTRO] aberta servidor=%s por=%s", s.getMatricula(), autor);
			LogSistemaRegistro.registrar(em, s.getUnidade(), "Pendência de cadastro profissional aberta para "
					+ texto(s.getNome()) + " (matrícula " + texto(s.getMatricula()) + "), prazo de " + PRAZO_DIAS
					+ " dias: " + motivo);
		} else {
			LogSistemaRegistro.registrar(em, s.getUnidade(), "Pendência de cadastro profissional mantida para "
					+ texto(s.getNome()) + " (matrícula " + texto(s.getMatricula()) + "): " + motivo);
		}
	}

	private static Map<String, Object> linha(Object[] r) {
		LocalDate prazo = InatividadeAcessoService.paraData(r[5]);
		if (prazo == null) {
			prazo = LocalDate.parse(texto(r[5]).substring(0, 10));
		}
		long dias = ChronoUnit.DAYS.between(LocalDate.now(), prazo);
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("idservidor", r[0]);
		m.put("matricula", texto(r[1]));
		m.put("nome", texto(r[2]));
		m.put("unidade", texto(r[3]));
		m.put("motivo", texto(r[4]));
		m.put("prazo", prazo.toString());
		m.put("diasRestantes", dias);
		m.put("vencida", dias < 0);
		return m;
	}

	private static final String SELECT_ABERTAS = "SELECT p.idservidor, p.matricula, s.nome, p.unidade, p.motivo, p.prazo "
			+ "FROM servidor_pendencia_cadastro p JOIN public.servidor s ON s.idservidor = p.idservidor "
			+ "WHERE p.resolvida_em IS NULL AND coalesce(s.ativo, true) ";

	/** Pendência aberta do próprio usuário (ou null). */
	@SuppressWarnings("unchecked")
	public Map<String, Object> minha(AuthenticatedUser u) {
		if (u == null || !tabelaPronta()) {
			return null;
		}
		List<Object[]> r = em.createNativeQuery(SELECT_ABERTAS + "AND s.matricula = :m")
			.setParameter("m", u.matricula())
			.setMaxResults(1)
			.getResultList();
		return r.isEmpty() ? null : linha(r.get(0));
	}

	/**
	 * Pendências que o líder acompanha: super usuário vê todas; administrativo, as da sua
	 * unidade; coordenador, as da equipe dos setores que coordena. Demais usuários: lista vazia.
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> equipe(AuthenticatedUser u) {
		List<Map<String, Object>> lista = new ArrayList<>();
		if (u == null || !tabelaPronta()) {
			return lista;
		}
		List<Object[]> r;
		if (u.hasPermission("ksuperusuario")) {
			r = em.createNativeQuery(SELECT_ABERTAS + "ORDER BY p.prazo").getResultList();
		} else if (u.hasPermission("kadm")) {
			r = em.createNativeQuery(SELECT_ABERTAS + "AND p.unidade = :u ORDER BY p.prazo")
				.setParameter("u", u.unidade())
				.getResultList();
		} else {
			r = em.createNativeQuery(SELECT_ABERTAS
					+ "AND p.matricula IN (SELECT eq.matricula FROM setor_coordenacao eq "
					+ "  JOIN setor_coordenacao co ON co.unidade = eq.unidade AND co.setor = eq.setor "
					+ "  WHERE co.matricula = :m AND co.papel = 'COORDENADOR' AND co.unidade = :u "
					+ "    AND co.removido_em IS NULL AND eq.removido_em IS NULL) ORDER BY p.prazo")
				.setParameter("m", u.matricula())
				.setParameter("u", u.unidade())
				.getResultList();
		}
		for (Object[] l : r) {
			lista.add(linha(l));
		}
		return lista;
	}
}
