package br.com.iv.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Confere o leito antes de alocar, transferir ou restaurar paciente no censo. Roda dentro da
 * transacao de quem chama e trava a linha do leito, para dois operadores nao passarem juntos.
 *
 * Leito ocupado nao bloqueia de vez: hospital lotado pode precisar, entao exige confirmacao
 * explicita (confirmarLeitoOcupado=true) e registra a decisao. Irmaos de parto multiplo dividem
 * leito sem confirmacao e sem limite de quantidade. Reserva de outro BAM so o NIR desfaz,
 * cancelando a reserva.
 */
@ApplicationScoped
public class LeitoOcupacaoGuard {

    private static final List<DateTimeFormatter> FORMATOS_NASCIMENTO = List.of(
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    @Inject
    EntityManager entityManager;

    public void conferir(int idleito, int bam, boolean confirmado, String acao) {
        if (idleito <= 0) {
            return;
        }
        entityManager.createNativeQuery(
                "SELECT idsetorlocalcenso FROM localcenso WHERE idsetorlocalcenso = :leito FOR UPDATE")
                .setParameter("leito", idleito)
                .getResultList();

        recusarSeReservadoParaOutro(idleito, bam);

        // Colunas 1 e 2 de cada linha: mae e nascimento, como todosIrmaos espera.
        @SuppressWarnings("unchecked")
        List<Object[]> ocupantes = entityManager.createNativeQuery(
                "SELECT n.nbam, p.mae, cast(p.nascimento as text) FROM novointernado n "
                        + "JOIN pacientes p ON p.idpaciente = n.pacientes_idpaciente "
                        + "WHERE n.local_idsetorlocalcenso = :leito AND n.alta = false AND n.nbam <> :bam")
                .setParameter("leito", idleito)
                .setParameter("bam", bam)
                .getResultList();
        if (ocupantes.isEmpty()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Object[]> paciente = entityManager.createNativeQuery(
                "SELECT p.idpaciente, p.mae, cast(p.nascimento as text) FROM bam b "
                        + "JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente WHERE b.idbam = :bam")
                .setParameter("bam", bam)
                .getResultList();
        if (!paciente.isEmpty()
                && todosIrmaos((String) paciente.get(0)[1], (String) paciente.get(0)[2], ocupantes)) {
            return;
        }

        int bamOcupante = ((Number) ocupantes.get(0)[0]).intValue();
        if (confirmado) {
            Log.warnf("%s confirmada: BAM %d no leito %d, ja ocupado pelo BAM %d", acao, bam, idleito, bamOcupante);
            return;
        }
        throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .entity(Map.of(
                        "erro", "Este leito está ocupado pelo BAM n. " + bamOcupante + ". Confirme antes de prosseguir.",
                        "campo", "LEITO",
                        "bamOcupante", bamOcupante))
                .build());
    }

    /** Encerra a reserva do BAM: consumida se usou o leito reservado; cancelada se foi para outro. */
    public void encerrarReservaDoBam(int bam, int idleito, String quem) {
        entityManager.createNativeQuery(
                "UPDATE leito_reserva SET status = CASE WHEN idleito = :leito THEN 'CONSUMIDA' ELSE 'CANCELADA' END, "
                        + "motivo_encerramento = CASE WHEN idleito = :leito THEN 'Paciente alocado no leito reservado' "
                        + "ELSE 'Paciente alocado em outro leito' END, "
                        + "encerrada_em = LOCALTIMESTAMP, encerrada_por = :quem WHERE bam = :bam AND status = 'ATIVA'")
                .setParameter("leito", idleito)
                .setParameter("quem", limitar(quem, 80))
                .setParameter("bam", bam)
                .executeUpdate();
    }

    private void recusarSeReservadoParaOutro(int idleito, int bam) {
        @SuppressWarnings("unchecked")
        List<Object[]> reservas = entityManager.createNativeQuery(
                "SELECT bam, to_char(expira_em, 'DD/MM HH24:MI') FROM leito_reserva "
                        + "WHERE idleito = :leito AND status = 'ATIVA' AND expira_em > LOCALTIMESTAMP AND bam <> :bam")
                .setParameter("leito", idleito)
                .setParameter("bam", bam)
                .getResultList();
        if (reservas.isEmpty()) {
            return;
        }
        Object[] reserva = reservas.get(0);
        throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                .entity(Map.of(
                        "erro", "Leito reservado para o BAM n. " + reserva[0] + " até " + reserva[1]
                                + ". Escolha outro leito ou peça ao NIR para cancelar a reserva.",
                        "campo", "LEITO_RESERVADO",
                        "bamReserva", ((Number) reserva[0]).intValue()))
                .build());
    }

    /** O paciente divide o leito sem confirmacao so se for irmao de todos os ocupantes, quantos forem. */
    static boolean todosIrmaos(String mae, String nascimento, List<Object[]> ocupantes) {
        return ocupantes.stream().allMatch(o -> irmaos(mae, nascimento, (String) o[1], (String) o[2]));
    }

    /**
     * Nao existe vinculo formal entre RN e mae: irmaos sao mesma mae e nascimento com ate um dia de
     * diferenca (parto que atravessa a meia-noite). Sem mae ou data legivel, nao presume.
     */
    static boolean irmaos(String mae1, String nascimento1, String mae2, String nascimento2) {
        String a = normalizar(mae1);
        if (a.isEmpty() || !a.equals(normalizar(mae2))) {
            return false;
        }
        LocalDate d1 = lerData(nascimento1);
        LocalDate d2 = lerData(nascimento2);
        return d1 != null && d2 != null && Math.abs(ChronoUnit.DAYS.between(d1, d2)) <= 1;
    }

    private static String normalizar(String nome) {
        return nome == null ? "" : nome.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static LocalDate lerData(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        for (DateTimeFormatter formato : FORMATOS_NASCIMENTO) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException e) {
                // tenta o proximo formato
            }
        }
        return null;
    }

    private static String limitar(String texto, int tamanho) {
        String valor = texto == null ? "" : texto;
        return valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }
}
