package br.com.iv.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import br.com.iv.dto.LogDto;
import br.com.iv.dto.SumarioObitoDto;
import br.com.iv.entity.SumarioObito;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.LogService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.transaction.Transactional;

@Path("sumario-obito") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class SumarioObitoController {
    static final String STATUS_CANCELADO = "CANCELADO";
    static final int LIMITE_OBITOS = 1000;
    static final int MOTIVO_MINIMO = 5;

    @Context ContainerRequestContext request;
    @Inject EntityManager entityManager;
    @Inject LogService logService;
    private AuthenticatedUser usuario() { Object value = request == null ? null : request.getProperty("ghbam.user"); if (!(value instanceof AuthenticatedUser user) || user.unidade() == null || user.unidade().isBlank()) throw new ForbiddenException("Unidade do usuario nao identificada."); return user; }
    @GET public List<SumarioObito> listar() { AuthenticatedUser user = usuario(); return SumarioObito.list("unidade = ?1 order by dataObito desc, id desc", user.unidade()); }

    /**
     * Lista unica dos BAMs da unidade com obito registrado (resumo de alta, desfecho do atendimento,
     * internacao) ou com sumario de obito ja feito, com o sumario mais recente de cada um.
     * Carregada uma vez pela tela; a busca por nome/prontuario/BAM e feita no navegador.
     */
    @GET @Path("/obitos") public List<Map<String, Object>> obitos() {
        AuthenticatedUser user = usuario();
        List<?> linhas = entityManager.createNativeQuery(SQL_OBITOS).setParameter("unidade", user.unidade()).getResultList();
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Object linha : linhas) {
            Object[] c = (Object[]) linha;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bam", texto(c[0]));
            item.put("nomePaciente", texto(c[1]));
            item.put("prontuario", texto(c[2]));
            item.put("dataNascimento", texto(c[3]));
            item.put("idade", texto(c[4]));
            item.put("sexo", texto(c[5]));
            item.put("endereco", texto(c[6]));
            item.put("numero", texto(c[7]));
            item.put("bairro", texto(c[8]));
            item.put("cep", texto(c[9]));
            item.put("estado", texto(c[10]));
            item.put("municipio", texto(c[11]));
            item.put("telefone", texto(c[12]));
            item.put("idResumoAlta", c[13]);
            item.put("dataInternacao", texto(c[14]));
            item.put("dataObito", texto(c[15]));
            item.put("horaObito", texto(c[16]));
            item.put("causaTerminal", texto(c[17]));
            item.put("sumarioAdmissao", texto(c[18]));
            item.put("idSumario", c[19]);
            item.put("statusSumario", texto(c[20]));
            item.put("sumarioPreenchido", Boolean.TRUE.equals(c[21]));
            item.put("setor", texto(c[22]));
            item.put("medicoAssistente", texto(c[23]));
            item.put("crmAssistente", texto(c[24]));
            item.put("semResumoAlta", Boolean.TRUE.equals(c[25]));
            resultado.add(item);
        }
        return resultado;
    }

    @GET @Path("/{id}") public SumarioObito buscar(@PathParam("id") Integer id) { AuthenticatedUser user = usuario(); SumarioObito item = SumarioObito.find("id = ?1 and unidade = ?2", id, user.unidade()).firstResult(); if (item == null) throw new NotFoundException(); return item; }
    @POST @Transactional public Response criar(SumarioObitoDto dto) { SumarioObito item = copiar(new SumarioObito(), dto, usuario()); item.persist(); return Response.status(Response.Status.CREATED).entity(item).build(); }
    @PUT @Path("/{id}") @Transactional public SumarioObito atualizar(@PathParam("id") Integer id, SumarioObitoDto dto) {
        AuthenticatedUser user = usuario();
        SumarioObito item = SumarioObito.find("id = ?1 and unidade = ?2", id, user.unidade()).firstResult();
        if (item == null) throw new NotFoundException();
        if (STATUS_CANCELADO.equals(item.getStatus())) throw new WebApplicationException("Sumario de obito cancelado nao pode ser alterado.", Response.Status.CONFLICT);
        return copiar(item, dto, user);
    }

    public record CancelamentoRequest(String motivo) {}

    /** Cancela sem apagar: guarda quem, quando e por que; a impressao passa a sair com marca d'agua. */
    @PUT @Path("/{id}/cancelar") @Transactional public SumarioObito cancelar(@PathParam("id") Integer id, CancelamentoRequest body) {
        AuthenticatedUser user = usuario();
        String motivo = motivoValido(body == null ? null : body.motivo());
        if (motivo == null) throw new BadRequestException("Informe o motivo do cancelamento (minimo " + MOTIVO_MINIMO + " caracteres).");
        SumarioObito item = SumarioObito.find("id = ?1 and unidade = ?2", id, user.unidade()).firstResult();
        if (item == null) throw new NotFoundException();
        if (STATUS_CANCELADO.equals(item.getStatus())) return item;
        item.setStatus(STATUS_CANCELADO);
        item.setCanceladoEm(LocalDateTime.now());
        item.setCanceladoPor(((user.nome() == null ? "" : user.nome().trim()) + " (" + user.matricula() + ")").trim());
        item.setMotivoCancelamento(motivo);
        auditar(user, "CANCELOU sumario=" + item.getId() + " bam=" + item.getBam());
        return item;
    }

    private SumarioObito copiar(SumarioObito e, SumarioObitoDto d, AuthenticatedUser u) {
        e.setUnidade(u.unidade()); e.setIdResumoAlta(d.getIdResumoAlta()); e.setBam(d.getBam()); e.setNomePaciente(d.getNomePaciente()); e.setProntuario(d.getProntuario()); e.setDataInternacao(d.getDataInternacao()); e.setDataNascimento(d.getDataNascimento()); e.setIdade(d.getIdade()); e.setSexo(d.getSexo()); e.setDataObito(d.getDataObito()); e.setHoraObito(d.getHoraObito()); e.setNumeroDO(d.getNumeroDO()); e.setSetor(d.getSetor()); e.setPermanenciaHospitalar(d.getPermanenciaHospitalar()); e.setPeriodoMulher(d.getPeriodoMulher()); e.setCausaExterna(d.getCausaExterna()); e.setEstatura(d.getEstatura()); e.setCorIdadePresumida(d.getCorIdadePresumida()); e.setSinaisAparentes(d.getSinaisAparentes()); e.setVestuario(d.getVestuario()); e.setCausaTerminal(d.getCausaTerminal()); e.setTempoTerminal(d.getTempoTerminal()); e.setCausaIntermediaria1(d.getCausaIntermediaria1()); e.setTempoIntermediaria1(d.getTempoIntermediaria1()); e.setCausaIntermediaria2(d.getCausaIntermediaria2()); e.setTempoIntermediaria2(d.getTempoIntermediaria2()); e.setCausaBasica(d.getCausaBasica()); e.setTempoBasica(d.getTempoBasica()); e.setCondicoesContribuintes(d.getCondicoesContribuintes()); e.setAgravoNotificacao(d.getAgravoNotificacao()); e.setAgravoEspecificar(d.getAgravoEspecificar()); e.setSumarioAdmissao(d.getSumarioAdmissao()); e.setMedicoAssistente(d.getMedicoAssistente()); e.setCrmAssistente(d.getCrmAssistente()); e.setDataAssinaturaAssistente(d.getDataAssinaturaAssistente()); e.setRevisor(d.getRevisor()); e.setCrmRevisor(d.getCrmRevisor()); e.setDataAssinaturaRevisor(d.getDataAssinaturaRevisor()); return e;
    }

    /** Lista BAMs com obito clinicamente registrado e pertencentes a unidade autenticada. */
    static final String SQL_OBITOS = """
            WITH obitos AS (
                SELECT CAST(r.bam AS integer) AS idbam FROM resumodealta r
                 WHERE r.obito = true AND COALESCE(r.status, 'ATIVO') <> 'CANCELADO'
                   AND r.bam ~ '^[0-9]{1,9}$'
                UNION
                -- Sem resumo de alta o obito tambem entra (selo "Sem resumo de alta"): antes o
                -- paciente que saiu por obito sem resumo sumia desta lista.
                SELECT b.idbam FROM bam b
                 JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
                 WHERE b.unidade = :unidade AND t.tipo_alta ILIKE '%bito%'
                UNION
                SELECT CAST(i.bam AS integer) FROM internacao i
                 WHERE i.motivodaalta ILIKE '%bito%' AND i.bam ~ '^[0-9]{1,9}$'
                UNION
                SELECT n.nbam FROM novointernado n
                 WHERE n.alta = true AND n.motivosaida ILIKE '%bito%'
            )
            SELECT b.idbam, p.nome, p.prontuario, p.nascimento, p.idade, p.sexo,
                   p.endereco, p.numero, p.bairro, p.cep, p.estado, p.municipio, p.telefone,
                   r.idresumoalta, r.datainternacao, COALESCE(r.dataobito, d.datasaida), r.horaobito, r.causamorte, r.relato,
                   s.id, s.status,
                   CASE WHEN s.id IS NOT NULL AND (
                     NULLIF(BTRIM(COALESCE(s.numerodo, '')), '') IS NOT NULL OR
                     NULLIF(BTRIM(COALESCE(s.causaterminal, '')), '') IS NOT NULL OR
                     NULLIF(BTRIM(COALESCE(s.causabasica, '')), '') IS NOT NULL OR
                     NULLIF(BTRIM(COALESCE(s.sumarioadmissao, '')), '') IS NOT NULL OR
                     NULLIF(BTRIM(COALESCE(s.medicoassistente, '')), '') IS NOT NULL
                   ) THEN true ELSE false END,
                   COALESCE(NULLIF(CONCAT_WS(' - ', c.enfermaria, CASE WHEN c.leito ~* '^leito' THEN c.leito WHEN c.leito IS NOT NULL THEN 'Leito ' || c.leito END), ''), i.local),
                   i.medicodeualta, m.crm,
                   (r.idresumoalta IS NULL)
            FROM obitos o
            JOIN bam b ON b.idbam = o.idbam AND b.unidade = :unidade
            JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
            LEFT JOIN LATERAL (
                SELECT r.idresumoalta, r.datainternacao, r.dataobito, r.horaobito, r.causamorte, r.relato
                FROM resumodealta r
                WHERE r.bam = CAST(b.idbam AS text) AND r.obito = true AND r.dataobito IS NOT NULL AND COALESCE(r.status, 'ATIVO') <> 'CANCELADO'
                ORDER BY r.idresumoalta DESC LIMIT 1
            ) r ON true
            LEFT JOIN LATERAL (
                SELECT s.id, s.status, s.numerodo, s.causaterminal, s.causabasica, s.sumarioadmissao, s.medicoassistente
                FROM sumarioobito s
                WHERE s.bam = CAST(b.idbam AS text) AND s.unidade = :unidade
                ORDER BY (s.status = 'CANCELADO'), s.id DESC LIMIT 1
            ) s ON true
            LEFT JOIN LATERAL (
                SELECT NULLIF(BTRIM(i.local), '') AS local, NULLIF(BTRIM(i.medicodeualta), '') AS medicodeualta
                FROM internacao i
                WHERE i.bam = CAST(b.idbam AS text)
                ORDER BY i.idninternado DESC LIMIT 1
            ) i ON true
            LEFT JOIN LATERAL (
                SELECT NULLIF(BTRIM(sc.setor), '') AS enfermaria, NULLIF(BTRIM(lc.local), '') AS leito
                FROM novointernado n
                LEFT JOIN setorcenso sc ON sc.idsetorcenso = n.setor_idsetorcenso
                LEFT JOIN localcenso lc ON lc.idsetorlocalcenso = n.local_idsetorlocalcenso
                WHERE n.nbam = b.idbam
                ORDER BY n.idninternado DESC LIMIT 1
            ) c ON true
            LEFT JOIN LATERAL (
                -- CRM so quando o nome digitado na alta bate com um unico CRM no cadastro de servidores.
                SELECT MIN(BTRIM(sv.crm)) AS crm
                FROM servidor sv
                WHERE i.medicodeualta IS NOT NULL
                  AND UPPER(BTRIM(sv.nome)) = UPPER(i.medicodeualta)
                  AND NULLIF(BTRIM(sv.crm), '') IS NOT NULL
                HAVING COUNT(DISTINCT BTRIM(sv.crm)) = 1
            ) m ON true
            LEFT JOIN LATERAL (
                -- Data da saida por obito (leito do NIR ou alta da internacao) quando nao ha resumo.
                SELECT COALESCE(
                    (SELECT n.datadaacao FROM novointernado n WHERE n.nbam = b.idbam AND n.motivosaida ILIKE '%bito%' AND n.datadaacao IS NOT NULL ORDER BY n.idninternado DESC LIMIT 1),
                    (SELECT i2.datasaida FROM internacao i2 WHERE i2.bam = CAST(b.idbam AS text) AND i2.motivodaalta ILIKE '%bito%' AND i2.datasaida IS NOT NULL ORDER BY i2.idninternado DESC LIMIT 1)
                ) AS datasaida
            ) d ON true
            -- b.data so ordena (nao e exibida como data do obito): sem ela o obito sem data
            -- ia para o fim e caia fora do LIMIT.
            ORDER BY COALESCE(r.dataobito, d.datasaida, b.data) DESC NULLS LAST, b.idbam DESC
            LIMIT """ + " " + LIMITE_OBITOS;
    static String motivoValido(String motivo) {
        if (motivo == null) return null;
        String limpo = motivo.trim();
        return limpo.length() >= MOTIVO_MINIMO ? limpo : null;
    }

    private void auditar(AuthenticatedUser user, String acao) {
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().toString().substring(0, 5));
        log.setUnidade(user.unidade());
        log.setEvento("SUMARIO_OBITO " + acao + " profissional=" + user.matricula());
        logService.salvarLogAsync(log);
    }

    private static String texto(Object valor) { return valor == null ? "" : String.valueOf(valor); }
}
