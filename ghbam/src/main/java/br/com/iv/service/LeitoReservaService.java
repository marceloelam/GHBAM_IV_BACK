package br.com.iv.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/** Reserva de leito pelo NIR: segura um leito livre para um BAM ate a alocacao ou o vencimento. */
@ApplicationScoped
public class LeitoReservaService {

    @ConfigProperty(name = "ghbam.nir.reserva.horas", defaultValue = "4")
    int horasDeValidade;

    @Inject
    EntityManager entityManager;

    @Transactional
    public Map<String, Object> reservar(int idleito, int bam, String unidade, String quem) {
        @SuppressWarnings("unchecked")
        List<Object[]> leitos = entityManager.createNativeQuery(
                "SELECT unidade, disponivel, excluido FROM localcenso WHERE idsetorlocalcenso = :leito FOR UPDATE")
                .setParameter("leito", idleito)
                .getResultList();
        if (leitos.isEmpty()) {
            throw new NotFoundException("Leito não encontrado.");
        }
        Object[] leito = leitos.get(0);
        if (!unidade.equals(leito[0])) {
            throw new ForbiddenException("O leito não pertence à unidade da sessão.");
        }
        if (!Boolean.TRUE.equals(leito[1]) || Boolean.TRUE.equals(leito[2])) {
            throw conflito("Este leito está bloqueado e não pode ser reservado.");
        }
        @SuppressWarnings("unchecked")
        List<Object> bams = entityManager.createNativeQuery("SELECT unidade FROM bam WHERE idbam = :bam")
                .setParameter("bam", bam)
                .getResultList();
        if (bams.isEmpty() || !unidade.equals(bams.get(0))) {
            throw new ForbiddenException("O BAM não pertence à unidade da sessão.");
        }
        if (contar("SELECT count(*) FROM novointernado WHERE local_idsetorlocalcenso = :id AND alta = false", idleito) > 0) {
            throw conflito("Este leito já está ocupado.");
        }
        if (contar("SELECT count(*) FROM novointernado WHERE nbam = :id AND alta = false", bam) > 0) {
            throw conflito("Este paciente já está alocado em um leito.");
        }

        entityManager.createNativeQuery(
                "UPDATE leito_reserva SET status = 'EXPIRADA', encerrada_em = expira_em "
                        + "WHERE status = 'ATIVA' AND expira_em <= LOCALTIMESTAMP AND (idleito = :leito OR bam = :bam)")
                .setParameter("leito", idleito)
                .setParameter("bam", bam)
                .executeUpdate();
        @SuppressWarnings("unchecked")
        List<Object> reservaOutroBam = entityManager.createNativeQuery(
                "SELECT bam FROM leito_reserva WHERE idleito = :leito AND status = 'ATIVA' AND bam <> :bam")
                .setParameter("leito", idleito)
                .setParameter("bam", bam)
                .getResultList();
        if (!reservaOutroBam.isEmpty()) {
            throw conflito("Este leito já está reservado para o BAM n. " + reservaOutroBam.get(0) + ".");
        }
        // Nova reserva do mesmo BAM substitui a anterior.
        entityManager.createNativeQuery(
                "UPDATE leito_reserva SET status = 'CANCELADA', encerrada_em = LOCALTIMESTAMP, encerrada_por = :quem, "
                        + "motivo_encerramento = 'Substituída por nova reserva' WHERE bam = :bam AND status = 'ATIVA'")
                .setParameter("quem", quem)
                .setParameter("bam", bam)
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO leito_reserva (idleito, bam, unidade, status, criada_por, expira_em) "
                        + "VALUES (:leito, :bam, :unidade, 'ATIVA', :quem, LOCALTIMESTAMP + make_interval(hours => :horas))")
                .setParameter("leito", idleito)
                .setParameter("bam", bam)
                .setParameter("unidade", unidade)
                .setParameter("quem", quem)
                .setParameter("horas", horasDeValidade)
                .executeUpdate();
        return listarAtivas(unidade).stream()
                .filter(r -> Integer.valueOf(bam).equals(r.get("bam")))
                .findFirst()
                .orElseThrow();
    }

    public List<Map<String, Object>> listarAtivas(String unidade) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = entityManager.createNativeQuery(
                "SELECT r.id, r.idleito, c.local, s.setor, r.bam, to_char(r.expira_em, 'DD/MM HH24:MI'), r.criada_por, s.idsetorcenso "
                        + "FROM leito_reserva r JOIN localcenso c ON c.idsetorlocalcenso = r.idleito "
                        + "JOIN setorcenso s ON s.idsetorcenso = c.setorcenso_idsetorcenso "
                        + "WHERE r.unidade = :unidade AND r.status = 'ATIVA' AND r.expira_em > LOCALTIMESTAMP "
                        + "ORDER BY r.expira_em")
                .setParameter("unidade", unidade)
                .getResultList();
        List<Map<String, Object>> reservas = new ArrayList<>();
        for (Object[] linha : linhas) {
            Map<String, Object> reserva = new HashMap<>();
            reserva.put("id", ((Number) linha[0]).longValue());
            reserva.put("idleito", ((Number) linha[1]).intValue());
            reserva.put("leito", linha[2]);
            reserva.put("setor", linha[3]);
            reserva.put("bam", ((Number) linha[4]).intValue());
            reserva.put("expiraEm", linha[5]);
            reserva.put("criadaPor", linha[6]);
            reserva.put("idsetor", ((Number) linha[7]).intValue());
            reservas.add(reserva);
        }
        return reservas;
    }

    @Transactional
    public void cancelar(long id, String unidade, String motivo, String quem) {
        String justificativa = motivo == null ? "" : motivo.trim();
        if (justificativa.length() < 5 || justificativa.length() > 200) {
            throw new BadRequestException("Informe o motivo do cancelamento (5 a 200 caracteres).");
        }
        int alteradas = entityManager.createNativeQuery(
                "UPDATE leito_reserva SET status = 'CANCELADA', encerrada_em = LOCALTIMESTAMP, encerrada_por = :quem, "
                        + "motivo_encerramento = :motivo WHERE id = :id AND unidade = :unidade AND status = 'ATIVA'")
                .setParameter("quem", quem)
                .setParameter("motivo", justificativa)
                .setParameter("id", id)
                .setParameter("unidade", unidade)
                .executeUpdate();
        if (alteradas == 0) {
            throw conflito("Reserva não encontrada ou já encerrada.");
        }
    }

    private long contar(String sql, int id) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter("id", id).getSingleResult()).longValue();
    }

    private static WebApplicationException conflito(String erro) {
        return new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .entity(Map.of("erro", erro, "campo", "RESERVA"))
                .build());
    }
}
