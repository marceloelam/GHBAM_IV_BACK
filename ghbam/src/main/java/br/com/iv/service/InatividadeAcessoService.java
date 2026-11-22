package br.com.iv.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.entity.Servidor;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Inatividade da conta (V265, decisão 22/11/2026): 30 dias sem acesso = alerta aos líderes;
 * 60 dias = conta suspensa até coordenador/administrativo/super reativar. Super usuário e
 * afastados (até a data informada) não são suspensos. Nada é apagado.
 */
@ApplicationScoped
public class InatividadeAcessoService {

	private static final Logger LOG = Logger.getLogger(InatividadeAcessoService.class);
	public static final int DIAS_ALERTA = 30;
	public static final int DIAS_SUSPENSAO = 60;

	@Inject
	EntityManager em;

	@Inject
	SecurityPermissionService securityPermissionService;

	private volatile boolean tabelaPronta;

	/** Servidores ativos, exceto super usuário, com o último acesso (login, atividade, cadastro ou reativação). */
	private static final String BASE = "SELECT s.idservidor, s.matricula, s.nome, s.unidade, c.afastado_ate, c.suspenso_em, "
			+ "GREATEST(c.cadastrado_em, c.reativado_em, (SELECT max(coalesce(sa.ultima_atividade, sa.data_login)) "
			+ "  FROM sessoes_ativas sa WHERE sa.matricula = s.matricula)) AS ultimo "
			+ "FROM public.servidor s JOIN servidor_acesso_controle c ON c.idservidor = s.idservidor "
			+ "WHERE coalesce(s.ativo, true) "
			+ "AND NOT EXISTS (SELECT 1 FROM servidor2 s2 WHERE s2.matricula = s.matricula AND coalesce(s2.superusuario, false))";

	private boolean tabelaPronta() {
		if (!tabelaPronta) {
			tabelaPronta = Boolean.TRUE.equals(em.createNativeQuery(
					"SELECT to_regclass('servidor_acesso_controle') IS NOT NULL").getSingleResult());
		}
		return tabelaPronta;
	}

	/** Rotina: registra servidores novos (conta a partir de agora) e suspende quem passou de 60 dias. */
	@Scheduled(every = "6h", delayed = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
	@Transactional
	void rotina() {
		try {
			if (!tabelaPronta()) {
				return;
			}
			em.createNativeQuery("INSERT INTO servidor_acesso_controle (idservidor, matricula, cadastrado_em) "
					+ "SELECT idservidor, matricula, now() FROM servidor ON CONFLICT (idservidor) DO NOTHING").executeUpdate();
			@SuppressWarnings("unchecked")
			List<Object[]> alvos = em.createNativeQuery("SELECT b.idservidor, b.matricula, b.nome, b.unidade FROM (" + BASE + ") b "
					+ "WHERE b.suspenso_em IS NULL AND b.ultimo < now() - interval '" + DIAS_SUSPENSAO + " days' "
					+ "AND (b.afastado_ate IS NULL OR b.afastado_ate < current_date)").getResultList();
			for (Object[] a : alvos) {
				em.createNativeQuery("UPDATE servidor_acesso_controle SET suspenso_em = now() WHERE idservidor = :id AND suspenso_em IS NULL")
					.setParameter("id", ((Number) a[0]).intValue())
					.executeUpdate();
				securityPermissionService.invalidateUserCache(String.valueOf(a[1]));
				LOG.infof("[INATIVIDADE] conta suspensa por %d dias sem acesso: matricula=%s", DIAS_SUSPENSAO, a[1]);
				LogSistemaRegistro.registrar(em, a[3] == null ? "" : a[3].toString(), null, null,
						"Conta suspensa automaticamente por " + DIAS_SUSPENSAO + " dias sem acesso: "
						+ (a[2] == null ? "" : a[2].toString().trim()) + " (matrícula " + a[1] + ")");
			}
		} catch (RuntimeException e) {
			LOG.warnf("Rotina de inatividade nao concluida: %s", e.getMessage());
		}
	}

	/** Conta suspensa por inatividade? Usado no login (SecurityPermissionService.acessoPermitido). */
	public boolean suspensa(Servidor s) {
		if (s == null || s.getIdservidor() == 0) {
			return false;
		}
		try {
			if (!tabelaPronta()) {
				return false;
			}
			return Boolean.TRUE.equals(em.createNativeQuery(
					"SELECT EXISTS (SELECT 1 FROM servidor_acesso_controle WHERE idservidor = :id AND suspenso_em IS NOT NULL)")
				.setParameter("id", s.getIdservidor())
				.getSingleResult());
		} catch (RuntimeException e) {
			LOG.warnf("Verificacao de inatividade falhou para servidor %d: %s", s.getIdservidor(), e.getMessage());
			return false;
		}
	}

	/** Mesmo escopo dos alertas de cadastro: super todas, adm a unidade, coordenador os setores que coordena. */
	private Query escopo(String sql, AuthenticatedUser u) {
		if (u.hasPermission("ksuperusuario")) {
			return em.createNativeQuery(sql);
		}
		if (u.hasPermission("kadm")) {
			return em.createNativeQuery(sql + " AND b.unidade = :u").setParameter("u", u.unidade());
		}
		return em.createNativeQuery(sql + " AND b.matricula IN (SELECT eq.matricula FROM setor_coordenacao eq "
				+ "  JOIN setor_coordenacao co ON co.unidade = eq.unidade AND co.setor = eq.setor "
				+ "  WHERE co.matricula = :m AND co.papel = 'COORDENADOR' AND co.unidade = :u "
				+ "    AND co.removido_em IS NULL AND eq.removido_em IS NULL)")
			.setParameter("m", u.matricula())
			.setParameter("u", u.unidade());
	}

	/** Contas da equipe com 30+ dias sem acesso (inclui suspensas), mais antigas primeiro. */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> inativos(AuthenticatedUser u) {
		List<Map<String, Object>> lista = new ArrayList<>();
		if (u == null || !tabelaPronta()) {
			return lista;
		}
		String sql = "SELECT b.idservidor, b.matricula, b.nome, b.unidade, b.ultimo, b.afastado_ate, b.suspenso_em FROM ("
				+ BASE + ") b WHERE b.ultimo < now() - interval '" + DIAS_ALERTA + " days'";
		List<Object[]> r = escopo(sql, u).getResultList();
		for (Object[] l : r) {
			LocalDateTime ultimo = paraDataHora(l[4]);
			LocalDate afastadoAte = paraData(l[5]);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("idservidor", l[0]);
			m.put("matricula", l[1] == null ? "" : l[1].toString());
			m.put("nome", l[2] == null ? "" : l[2].toString().trim());
			m.put("unidade", l[3] == null ? "" : l[3].toString());
			m.put("diasSemAcesso", ultimo == null ? null : ChronoUnit.DAYS.between(ultimo.toLocalDate(), LocalDate.now()));
			m.put("afastadoAte", afastadoAte == null || afastadoAte.isBefore(LocalDate.now()) ? null : afastadoAte.toString());
			m.put("suspensa", l[6] != null);
			lista.add(m);
		}
		lista.sort((a, b) -> Long.compare(
				b.get("diasSemAcesso") == null ? 0 : ((Number) b.get("diasSemAcesso")).longValue(),
				a.get("diasSemAcesso") == null ? 0 : ((Number) a.get("diasSemAcesso")).longValue()));
		return lista;
	}

	/** O Hibernate (Quarkus) devolve java.time; JDBC puro devolve java.sql. Aceita os dois. */
	static LocalDateTime paraDataHora(Object v) {
		if (v instanceof LocalDateTime t) return t;
		if (v instanceof Timestamp t) return t.toLocalDateTime();
		if (v instanceof java.time.OffsetDateTime t) return t.toLocalDateTime();
		if (v instanceof java.time.Instant t) return LocalDateTime.ofInstant(t, java.time.ZoneId.systemDefault());
		if (v instanceof java.util.Date t) return new Timestamp(t.getTime()).toLocalDateTime();
		return null;
	}

	static LocalDate paraData(Object v) {
		if (v instanceof LocalDate d) return d;
		if (v instanceof java.sql.Date d) return d.toLocalDate();
		LocalDateTime dt = paraDataHora(v);
		return dt == null ? null : dt.toLocalDate();
	}

	private void exigirLider(AuthenticatedUser u, int idservidor) {
		if (u == null || !tabelaPronta()) {
			throw new IllegalStateException("Controle de inatividade indisponível.");
		}
		String sql = "SELECT count(*) FROM (" + BASE.replace("WHERE coalesce(s.ativo, true) ", "WHERE true ")
				+ ") b WHERE b.idservidor = :id";
		Number n = (Number) escopo(sql, u).setParameter("id", idservidor).getSingleResult();
		if (n.intValue() == 0) {
			throw new IllegalStateException("Somente o coordenador do setor, o administrativo da unidade ou o super usuário pode fazer isso.");
		}
	}

	private String matriculaDe(int idservidor) {
		Object m = em.createNativeQuery("SELECT matricula FROM servidor WHERE idservidor = :id")
			.setParameter("id", idservidor).getSingleResult();
		return m == null ? "" : m.toString();
	}

	@Transactional
	public void reativar(AuthenticatedUser u, int idservidor) {
		exigirLider(u, idservidor);
		em.createNativeQuery("UPDATE servidor_acesso_controle SET suspenso_em = NULL, reativado_em = now(), reativado_por = :por "
				+ "WHERE idservidor = :id")
			.setParameter("por", u.matricula())
			.setParameter("id", idservidor)
			.executeUpdate();
		String matricula = matriculaDe(idservidor);
		securityPermissionService.invalidateUserCache(matricula);
		LOG.infof("[INATIVIDADE] conta reativada: matricula=%s por=%s", matricula, u.matricula());
		LogSistemaRegistro.registrar(em, u.unidade(), u.nome(), u.matricula(),
				"Conta reativada (inatividade): matrícula " + matricula);
	}

	@Transactional
	public void afastar(AuthenticatedUser u, int idservidor, LocalDate ate, String motivo) {
		exigirLider(u, idservidor);
		if (ate == null || ate.isBefore(LocalDate.now()) || ate.isAfter(LocalDate.now().plusYears(1))) {
			throw new IllegalArgumentException("Informe a data de retorno (de hoje até 1 ano).");
		}
		em.createNativeQuery("UPDATE servidor_acesso_controle SET afastado_ate = :ate, afastado_motivo = :motivo, afastado_por = :por "
				+ "WHERE idservidor = :id")
			.setParameter("ate", ate)
			.setParameter("motivo", motivo == null ? "" : motivo.trim())
			.setParameter("por", u.matricula())
			.setParameter("id", idservidor)
			.executeUpdate();
		LOG.infof("[INATIVIDADE] afastado ate %s: idservidor=%d por=%s", ate, idservidor, u.matricula());
		LogSistemaRegistro.registrar(em, u.unidade(), u.nome(), u.matricula(),
				"Afastamento registrado até " + ate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
				+ " (conta não será suspensa por inatividade): matrícula " + matriculaDe(idservidor)
				+ (motivo == null || motivo.isBlank() ? "" : " — motivo: " + motivo.trim()));
	}
}
