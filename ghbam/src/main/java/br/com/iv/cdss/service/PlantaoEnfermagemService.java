package br.com.iv.cdss.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.cdss.dto.PassagemPlantaoRequest;
import br.com.iv.cdss.entity.PassagemPlantao;
import br.com.iv.entity.PrescricaoAprazamentoChecagem;
import br.com.iv.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.repository.ProntuarioClinicoItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Painel do plantao de enfermagem: internados sem alta da unidade da sessao, com
 * o que precisa de atencao em cada leito - alergia, valor critico sem ciencia,
 * reconciliacao pendente e horario de medicacao aprazado e nao checado.
 * So leitura; falha num paciente nao derruba o painel.
 */
@ApplicationScoped
public class PlantaoEnfermagemService {
    static final int LIMITE_PACIENTES = 300;
    static final int TOLERANCIA_ATRASO_MIN = 30;
    static final int JANELA_APRAZAMENTO_HORAS = 36;

    @Inject
    EntityManager em;

    @Inject
    ProntuarioClinicoItemRepository prontuarioRepository;

    @Inject
    ResultadoCriticoService resultadoCriticoService;

    @Inject
    ReconciliacaoMedicamentosService reconciliacaoService;

    @Inject
    DispositivoInvasivoService dispositivoService;

    @SuppressWarnings("unchecked")
    public Map<String, Object> montar(String unidade) {
        if (unidade == null || unidade.isBlank()) {
            throw new SecurityException("Sessao sem unidade identificada.");
        }
        List<Object[]> linhas = em.createNativeQuery(
                "select distinct on (n.nbam) n.nbam, p.idpaciente, p.nome, p.prontuario, s.setor, l.local "
                        + "from novointernado n "
                        + "join bam b on b.idbam = n.nbam "
                        + "join pacientes p on p.idpaciente = b.paciente_idpaciente "
                        + "left join setorcenso s on s.idsetorcenso = n.setor_idsetorcenso "
                        + "left join localcenso l on l.idsetorlocalcenso = n.local_idsetorlocalcenso "
                        + "where n.unidade = :unidade and coalesce(n.alta, false) = false "
                        + "order by n.nbam desc")
                .setParameter("unidade", unidade.trim())
                .setMaxResults(LIMITE_PACIENTES)
                .getResultList();

        List<Integer> bams = new ArrayList<>();
        for (Object[] l : linhas) {
            bams.add(((Number) l[0]).intValue());
        }
        Map<Integer, List<String>> atrasos = aprazamentosAtrasados(bams, LocalDateTime.now());
        Map<Integer, Map<String, Object>> passagens = ultimasPassagens(bams, LocalDateTime.now());
        Map<Integer, List<Map<String, Object>>> dispositivos = dispositivoService == null
                ? Map.of() : dispositivoService.ativosPorBam(bams);

        List<Map<String, Object>> pacientes = new ArrayList<>();
        for (Object[] l : linhas) {
            int idbam = ((Number) l[0]).intValue();
            int idpaciente = ((Number) l[1]).intValue();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("idbam", idbam);
            p.put("idpaciente", idpaciente);
            p.put("nome", l[2]);
            p.put("prontuario", l[3]);
            p.put("setor", l[4]);
            p.put("leito", l[5]);
            p.put("aprazamentosAtrasados", atrasos.getOrDefault(idbam, List.of()));
            p.put("ultimaPassagem", passagens.get(idbam));
            p.put("dispositivos", dispositivos.getOrDefault(idbam, List.of()));
            try {
                p.put("alergias", alergias(idpaciente));
                p.put("criticosPendentes", resultadoCriticoService.listarPorBam(idbam).stream()
                        .filter(c -> !c.ciente())
                        .map(c -> c.exame() + " " + c.valor())
                        .toList());
                p.put("reconciliacaoPendente", reconciliacaoService.listar(idbam, idpaciente).stream()
                        .filter(r -> r.get("decisao") == null)
                        .count());
            } catch (RuntimeException e) {
                p.put("erro", "Nao foi possivel carregar todas as informacoes deste paciente.");
            }
            pacientes.add(p);
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("unidade", unidade.trim());
        resposta.put("geradoEm", LocalDateTime.now());
        resposta.put("limite", LIMITE_PACIENTES);
        resposta.put("pacientes", pacientes);
        return resposta;
    }

    @Transactional
    public Map<String, Object> registrarPassagem(PassagemPlantaoRequest request, AuthenticatedUser usuario) {
        exigirProfissional(usuario);
        if (request == null || request.getIdbam() == null) {
            throw new IllegalArgumentException("Informe o leito (BAM).");
        }
        String situacao = texto(request.getSituacao());
        if (situacao.length() < 5) {
            throw new IllegalArgumentException("Descreva a situacao do paciente (S do SBAR).");
        }
        Integer idpaciente = internadoNaUnidade(request.getIdbam(), usuario.unidade());
        if (idpaciente == null) {
            throw new IllegalArgumentException("Este BAM nao esta internado na sua unidade.");
        }
        PassagemPlantao p = new PassagemPlantao();
        p.setUnidade(usuario.unidade().trim());
        p.setIdbam(request.getIdbam());
        p.setIdpaciente(idpaciente);
        p.setSituacao(situacao);
        p.setHistorico(texto(request.getHistorico()));
        p.setAvaliacao(texto(request.getAvaliacao()));
        p.setRecomendacao(texto(request.getRecomendacao()));
        p.setEntreguePor(usuario.nome());
        p.setEntreguePorMatricula(usuario.matricula());
        p.persist();
        return passagemMap(p);
    }

    @Transactional
    public Map<String, Object> receberPassagem(long id, AuthenticatedUser usuario) {
        exigirProfissional(usuario);
        PassagemPlantao p = PassagemPlantao.findById(id);
        if (p == null || !p.getUnidade().equalsIgnoreCase(usuario.unidade().trim())) {
            throw new IllegalArgumentException("Passagem de plantao nao encontrada na sua unidade.");
        }
        if (p.getRecebidoEm() == null) { // o primeiro recebimento vale
            p.setRecebidoPor(usuario.nome());
            p.setRecebidoPorMatricula(usuario.matricula());
            p.setRecebidoEm(LocalDateTime.now());
        }
        return passagemMap(p);
    }

    /** Passagens de plantao da unidade num dia, para impressao. So leitura. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> passagensDoDia(String data, AuthenticatedUser usuario) {
        exigirProfissional(usuario);
        java.time.LocalDate dia;
        try {
            dia = java.time.LocalDate.parse(data == null ? "" : data.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data invalida; use aaaa-mm-dd.");
        }
        List<Object[]> linhas = em.createNativeQuery(
                "select pp.id, pp.idbam, p.nome, p.prontuario, leito.setor, leito.local, pp.situacao, pp.historico, "
                        + "pp.avaliacao, pp.recomendacao, pp.entregue_por, pp.entregue_em, pp.recebido_por, pp.recebido_em "
                        + "from passagem_plantao pp "
                        + "left join pacientes p on p.idpaciente = pp.idpaciente "
                        + "left join lateral (select s.setor, l.local from novointernado n "
                        + "  left join setorcenso s on s.idsetorcenso = n.setor_idsetorcenso "
                        + "  left join localcenso l on l.idsetorlocalcenso = n.local_idsetorlocalcenso "
                        + "  where n.nbam = pp.idbam limit 1) leito on true "
                        + "where pp.unidade = :unidade and pp.entregue_em >= :inicio and pp.entregue_em < :fim "
                        + "order by pp.entregue_em, leito.local")
                .setParameter("unidade", usuario.unidade().trim())
                .setParameter("inicio", dia.atStartOfDay())
                .setParameter("fim", dia.plusDays(1).atStartOfDay())
                .getResultList();
        String[] campos = {"id", "idbam", "nome", "prontuario", "setor", "leito", "situacao", "historico",
                "avaliacao", "recomendacao", "entreguePor", "entregueEm", "recebidoPor", "recebidoEm"};
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object[] l : linhas) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < campos.length; i++) {
                m.put(campos[i], l[i]);
            }
            lista.add(m);
        }
        return lista;
    }

    private Map<Integer, Map<String, Object>> ultimasPassagens(List<Integer> bams, LocalDateTime agora) {
        Map<Integer, Map<String, Object>> porBam = new HashMap<>();
        if (bams.isEmpty()) return porBam;
        for (PassagemPlantao p : PassagemPlantao.<PassagemPlantao>list(
                "idbam in ?1 and entregueEm >= ?2 order by entregueEm desc", bams, agora.minusHours(24))) {
            porBam.putIfAbsent(p.getIdbam(), passagemMap(p));
        }
        return porBam;
    }

    Integer internadoNaUnidade(int idbam, String unidade) {
        List<?> r = em.createNativeQuery(
                "select b.paciente_idpaciente from novointernado n join bam b on b.idbam = n.nbam "
                        + "where n.nbam = :bam and n.unidade = :unidade and coalesce(n.alta, false) = false")
                .setParameter("bam", idbam)
                .setParameter("unidade", unidade.trim())
                .setMaxResults(1)
                .getResultList();
        return r.isEmpty() || r.get(0) == null ? null : ((Number) r.get(0)).intValue();
    }

    private static Map<String, Object> passagemMap(PassagemPlantao p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("situacao", p.getSituacao());
        m.put("historico", p.getHistorico());
        m.put("avaliacao", p.getAvaliacao());
        m.put("recomendacao", p.getRecomendacao());
        m.put("entreguePor", p.getEntreguePor());
        m.put("entregueEm", p.getEntregueEm());
        m.put("recebidoPor", p.getRecebidoPor());
        m.put("recebidoEm", p.getRecebidoEm());
        return m;
    }

    static void exigirProfissional(AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()
                || usuario.unidade() == null || usuario.unidade().isBlank()) {
            throw new SecurityException("Sessao sem profissional ou unidade identificados.");
        }
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private List<String> alergias(int idpaciente) {
        return prontuarioRepository.listarPorPacienteTipo(idpaciente, "ALERGIA", "TODAS").stream()
                .filter(i -> "ATIVO".equalsIgnoreCase(i.getStatus()))
                .map(ProntuarioClinicoItem::getTitulo)
                .filter(t -> t != null && !t.isBlank())
                .toList();
    }

    private Map<Integer, List<String>> aprazamentosAtrasados(List<Integer> bams, LocalDateTime agora) {
        Map<Integer, List<String>> porBam = new HashMap<>();
        if (bams.isEmpty()) return porBam;
        List<PrescricaoAprazamentoChecagem> pendentes = PrescricaoAprazamentoChecagem
                .<PrescricaoAprazamentoChecagem>list("idbam in ?1 and checado = false and aprazadoEm >= ?2",
                        bams, agora.minusHours(JANELA_APRAZAMENTO_HORAS));
        for (PrescricaoAprazamentoChecagem a : pendentes) {
            LocalDateTime previsto = horarioPrevisto(a.getAprazadoEm(), a.getHorarioAprazado());
            if (previsto != null && previsto.plusMinutes(TOLERANCIA_ATRASO_MIN).isBefore(agora)) {
                porBam.computeIfAbsent(a.getIdbam(), k -> new ArrayList<>()).add(a.getHorarioAprazado());
            }
        }
        return porBam;
    }

    /**
     * Data/hora prevista do horario aprazado. O aprazamento guarda so "HH:mm";
     * horario anterior ao momento do aprazamento e da madrugada seguinte
     * (aprazado as 20h para as 06h).
     */
    static LocalDateTime horarioPrevisto(LocalDateTime aprazadoEm, String horario) {
        if (aprazadoEm == null || horario == null || horario.isBlank()) return null;
        try {
            LocalTime hora = LocalTime.parse(horario.trim().length() == 4 ? "0" + horario.trim() : horario.trim());
            LocalDateTime previsto = aprazadoEm.toLocalDate().atTime(hora);
            if (previsto.isBefore(aprazadoEm.minusHours(1))) {
                previsto = previsto.plusDays(1);
            }
            return previsto;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
