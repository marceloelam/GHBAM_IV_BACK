package br.com.iv.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import br.com.iv.dto.RhDto;
import br.com.iv.entity.Certificacao;
import br.com.iv.entity.Plantao;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Treinamento;
import br.com.iv.entity.TreinamentoParticipante;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RhService {

    @Inject
    EntityManager em;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ===== PLANTÕES =====

    private boolean jaTemPlantaoNoDia(int servidorId, java.time.LocalDate dia) {
        Number n = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM plantao WHERE servidor_idservidor = :sid AND CAST(data_inicio AS DATE) = :dia")
            .setParameter("sid", servidorId)
            .setParameter("dia", dia)
            .getSingleResult();
        return n != null && n.longValue() > 0;
    }

    @Transactional
    public Plantao abrirPlantao(RhDto.PlantaoRequest req) {
        if (req.servidorId != null && req.dataInicio != null
                && jaTemPlantaoNoDia(req.servidorId, req.dataInicio.toLocalDate()))
            throw new IllegalStateException("Servidor já possui plantão registrado nesse dia");
        Plantao p = new Plantao();
        p.setServidor(em.find(Servidor.class, req.servidorId));
        p.setUnidade(req.unidade);
        p.setDataInicio(req.dataInicio);
        p.setDataFim(req.dataFim);
        p.setTipo(req.tipo);
        p.setSetor(req.setor);
        p.setObs(req.obs);
        if (req.cadastrouId != null) p.setCadastrou(em.find(Servidor.class, req.cadastrouId));
        em.persist(p);
        return p;
    }

    @Transactional
    public void encerrarPlantao(int id) {
        Plantao p = em.find(Plantao.class, id);
        if (p != null && p.getDataFim() == null) {
            p.setDataFim(java.time.LocalDateTime.now());
            em.merge(p);
        }
    }

    @Transactional
    public int[] abrirPlantaoLote(List<RhDto.PlantaoRequest> lista) {
        int salvos = 0, ignorados = 0;
        for (RhDto.PlantaoRequest req : lista) {
            try {
                if (req.servidorId == null || req.dataInicio == null) { ignorados++; continue; }
                if (jaTemPlantaoNoDia(req.servidorId, req.dataInicio.toLocalDate())) { ignorados++; continue; }
                Plantao p = new Plantao();
                p.setServidor(em.find(Servidor.class, req.servidorId));
                if (p.getServidor() == null) { ignorados++; continue; }
                p.setUnidade(req.unidade);
                p.setDataInicio(req.dataInicio);
                p.setDataFim(req.dataFim);
                p.setTipo(req.tipo);
                p.setSetor(req.setor);
                p.setObs(req.obs);
                if (req.cadastrouId != null) p.setCadastrou(em.find(Servidor.class, req.cadastrouId));
                em.persist(p);
                salvos++;
            } catch (Exception e) {
                e.printStackTrace();
                ignorados++;
            }
        }
        return new int[]{ salvos, ignorados };
    }

    @Transactional
    public void atualizarPlantao(int id, RhDto.PlantaoRequest req) {
        Plantao p = em.find(Plantao.class, id);
        if (p == null) return;
        if (req.servidorId != null) p.setServidor(em.find(Servidor.class, req.servidorId));
        p.setDataInicio(req.dataInicio);
        p.setDataFim(req.dataFim);
        p.setTipo(req.tipo);
        p.setSetor(req.setor);
        p.setObs(req.obs);
        em.merge(p);
    }

    @Transactional
    public void excluirPlantao(int id) {
        Plantao p = em.find(Plantao.class, id);
        if (p != null) em.remove(p);
    }

    public List<RhDto.ServidorSimples> listarServidoresPorTipo(String unidade, String tipo) {
        String filtroTipo = "";
        if (tipo != null) {
            switch (tipo.toUpperCase()) {
                case "MÉDICO":
                case "MEDICO":
                    filtroTipo = "AND s.medico = true "; break;
                case "ENFERMAGEM":
                    filtroTipo = "AND (s.enfermagem = true OR s.tcenfermagem = true) "; break;
                case "TÉCNICO":
                case "TECNICO":
                    filtroTipo = "AND s.tcenfermagem = true "; break;
                case "ADMINISTRATIVO":
                    filtroTipo = "AND (s.administracao = true OR s.recepcao = true OR s.subadm = true) "; break;
                default:
                    filtroTipo = "";
            }
        }
        String sql = "SELECT s.idservidor, s.nome, s.matricula, s.cargo "
            + "FROM servidor s "
            + "WHERE s.ativo = true "
            + "AND (s.unidade = :unidade OR s.unidade LIKE :unidadeLike) "
            + filtroTipo
            + "ORDER BY s.nome";
        List<RhDto.ServidorSimples> lista = new ArrayList<>();
        try {
            Query q = em.createNativeQuery(sql);
            q.setParameter("unidade", unidade);
            q.setParameter("unidadeLike", unidade + "%");
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            for (Object[] r : rows) {
                lista.add(new RhDto.ServidorSimples(
                    ((Number) r[0]).intValue(), txt(r[1]), txt(r[2]), txt(r[3])));
            }
        } catch (Exception ignored) {}
        return lista;
    }

    public List<RhDto.PlantaoResponse> listarPlantoes(String unidade, LocalDate inicio, LocalDate fim) {
        String sql = "SELECT p.idplantao, s.idservidor, s.nome, s.matricula, p.unidade, "
            + "p.data_inicio, p.data_fim, p.tipo, p.setor, p.obs "
            + "FROM plantao p JOIN servidor s ON s.idservidor = p.servidor_idservidor "
            + "WHERE p.unidade = :unidade "
            + "AND CAST(p.data_inicio AS DATE) BETWEEN :inicio AND :fim "
            + "ORDER BY p.data_inicio DESC";
        List<RhDto.PlantaoResponse> lista = new ArrayList<>();
        try {
            Query q = em.createNativeQuery(sql);
            q.setParameter("unidade", unidade);
            q.setParameter("inicio", inicio);
            q.setParameter("fim", fim);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            for (Object[] r : rows) {
                var ini = r[5] != null ? ((java.sql.Timestamp) r[5]).toLocalDateTime() : null;
                var fim2 = r[6] != null ? ((java.sql.Timestamp) r[6]).toLocalDateTime() : null;
                String duracao = calcularDuracao(ini, fim2);
                lista.add(new RhDto.PlantaoResponse(
                    ((Number) r[0]).intValue(),
                    ((Number) r[1]).intValue(),
                    txt(r[2]), txt(r[3]), txt(r[4]),
                    ini != null ? ini.format(DT_FMT) : "",
                    fim2 != null ? fim2.format(DT_FMT) : "",
                    txt(r[7]), txt(r[8]), txt(r[9]),
                    duracao, fim2 == null || fim2.isAfter(java.time.LocalDateTime.now())));
            }
        } catch (Exception ignored) {}
        return lista;
    }

    // ===== PRODUTIVIDADE =====

    public List<RhDto.ProdutividadeItem> produtividade(String unidade, LocalDate inicio, LocalDate fim, Integer servidorId) {
        String filtroServidor = servidorId != null ? " AND s.idservidor = :servidorId " : "";
        String sql = "SELECT s.nome, s.matricula, COALESCE(s.cargo, '') as cargo, "
            + "COUNT(p.idplantao) as total_plantoes, "
            + "COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(p.data_fim, NOW()) - p.data_inicio)) / 3600.0), 0) as total_horas, "
            + "STRING_AGG(DISTINCT COALESCE(p.tipo, ''), ', ') FILTER (WHERE p.tipo IS NOT NULL AND p.tipo <> '') as tipos, "
            + "STRING_AGG(DISTINCT COALESCE(p.setor, ''), ', ') FILTER (WHERE p.setor IS NOT NULL AND p.setor <> '') as setores, "
            + "(SELECT COUNT(DISTINCT a2.idatendimento) "
            + " FROM plantao p2 "
            + " JOIN atendimento a2 ON "
            + "   LOWER(TRIM(COALESCE(a2.nomedomedico, ''))) = LOWER(TRIM(s.nome)) "
            + "   AND (a2.data + CASE WHEN a2.hora ~ '^[0-9]{2}:[0-9]{2}$' THEN a2.hora::time ELSE TIME '00:00' END) "
            + "       BETWEEN p2.data_inicio AND COALESCE(p2.data_fim, NOW()) "
            + " JOIN bam b2 ON b2.idbam = a2.bam "
            + "   AND (b2.unidade = :unidade OR b2.unidade LIKE :unidadeLike) "
            + " WHERE p2.servidor_idservidor = s.idservidor "
            + " AND CAST(p2.data_inicio AS DATE) BETWEEN :inicio AND :fim) as total_atendimentos "
            + "FROM servidor s "
            + "JOIN plantao p ON p.servidor_idservidor = s.idservidor "
            + "WHERE (p.unidade = :unidade OR p.unidade LIKE :unidadeLike) "
            + "AND CAST(p.data_inicio AS DATE) BETWEEN :inicio AND :fim "
            + filtroServidor
            + "GROUP BY s.idservidor, s.nome, s.matricula, s.cargo "
            + "ORDER BY COUNT(p.idplantao) DESC, total_horas DESC "
            + "LIMIT 100";
        List<RhDto.ProdutividadeItem> lista = new ArrayList<>();
        String periodo = inicio.format(D_FMT) + " a " + fim.format(D_FMT);
        try {
            Query q = em.createNativeQuery(sql);
            q.setParameter("unidade", unidade);
            q.setParameter("unidadeLike", unidade + "%");
            q.setParameter("inicio", inicio);
            q.setParameter("fim", fim);
            if (servidorId != null) q.setParameter("servidorId", servidorId);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            for (Object[] r : rows) {
                lista.add(new RhDto.ProdutividadeItem(
                    txt(r[0]), txt(r[1]), txt(r[2]),
                    ((Number) r[3]).longValue(),
                    r[4] != null ? ((Number) r[4]).doubleValue() : 0,
                    r[7] != null ? ((Number) r[7]).longValue() : 0,
                    txt(r[5]), txt(r[6]), periodo));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ===== TREINAMENTOS =====

    @Transactional
    public Treinamento criarTreinamento(RhDto.TreinamentoRequest req) {
        Treinamento t = new Treinamento();
        t.setTitulo(req.titulo);
        t.setDescricao(req.descricao);
        t.setDataInicio(req.dataInicio);
        t.setDataFim(req.dataFim);
        t.setCargaHoraria(req.cargaHoraria);
        t.setCategoria(req.categoria);
        t.setUnidade(req.unidade);
        t.setObrigatorio(req.obrigatorio);
        if (req.cadastrouId != null) t.setCadastrou(em.find(Servidor.class, req.cadastrouId));
        em.persist(t);

        if (req.participantes != null) {
            for (Integer sid : req.participantes) {
                TreinamentoParticipante tp = new TreinamentoParticipante();
                tp.setTreinamento(t);
                tp.setServidor(em.find(Servidor.class, sid));
                tp.setStatus("PENDENTE");
                em.persist(tp);
            }
        }
        return t;
    }

    @Transactional
    public void concluirParticipante(int treinamentoId, int servidorId) {
        String sql = "UPDATE treinamento_participante SET status = 'CONCLUIDO', data_conclusao = CURRENT_DATE "
            + "WHERE treinamento_idtreinamento = :tid AND servidor_idservidor = :sid";
        Query q = em.createNativeQuery(sql);
        q.setParameter("tid", treinamentoId);
        q.setParameter("sid", servidorId);
        q.executeUpdate();
    }

    @Transactional
    public void excluirTreinamento(int id) {
        Treinamento t = em.find(Treinamento.class, id);
        if (t != null) em.remove(t);
    }

    public List<RhDto.TreinamentoResponse> listarTreinamentos(String unidade) {
        String sql = "SELECT t.idtreinamento, t.titulo, t.descricao, t.data_inicio, t.data_fim, "
            + "t.carga_horaria, t.categoria, t.unidade, t.obrigatorio, "
            + "COUNT(tp.id), COUNT(tp.id) FILTER (WHERE tp.status = 'CONCLUIDO') "
            + "FROM treinamento t LEFT JOIN treinamento_participante tp ON tp.treinamento_idtreinamento = t.idtreinamento "
            + "WHERE t.unidade = :unidade "
            + "GROUP BY t.idtreinamento ORDER BY t.data_inicio DESC";
        List<RhDto.TreinamentoResponse> lista = new ArrayList<>();
        try {
            Query q = em.createNativeQuery(sql);
            q.setParameter("unidade", unidade);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            for (Object[] r : rows) {
                lista.add(new RhDto.TreinamentoResponse(
                    ((Number) r[0]).intValue(), txt(r[1]), txt(r[2]),
                    r[3] != null ? r[3].toString() : "",
                    r[4] != null ? r[4].toString() : "",
                    r[5] != null ? ((Number) r[5]).intValue() : null,
                    txt(r[6]), txt(r[7]),
                    r[8] != null && (Boolean) r[8],
                    r[9] != null ? ((Number) r[9]).intValue() : 0,
                    r[10] != null ? ((Number) r[10]).intValue() : 0));
            }
        } catch (Exception ignored) {}
        return lista;
    }

    // ===== CERTIFICAÇÕES =====

    @Transactional
    public Certificacao registrarCertificacao(RhDto.CertificacaoRequest req) {
        Certificacao c = new Certificacao();
        c.setServidor(em.find(Servidor.class, req.servidorId));
        c.setTitulo(req.titulo);
        c.setInstituicao(req.instituicao);
        c.setDataEmissao(req.dataEmissao);
        c.setDataValidade(req.dataValidade);
        c.setArquivo(req.arquivo);
        c.setUnidade(req.unidade);
        em.persist(c);
        return c;
    }

    @Transactional
    public void excluirCertificacao(int id) {
        Certificacao c = em.find(Certificacao.class, id);
        if (c != null) em.remove(c);
    }

    public List<RhDto.CertificacaoResponse> listarCertificacoes(String unidade) {
        String sql = "SELECT c.idcertificacao, s.nome, c.titulo, c.instituicao, "
            + "c.data_emissao, c.data_validade, c.unidade "
            + "FROM certificacao c JOIN servidor s ON s.idservidor = c.servidor_idservidor "
            + "WHERE c.unidade = :unidade ORDER BY c.data_validade ASC NULLS LAST";
        List<RhDto.CertificacaoResponse> lista = new ArrayList<>();
        try {
            Query q = em.createNativeQuery(sql);
            q.setParameter("unidade", unidade);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            for (Object[] r : rows) {
                LocalDate validade = r[5] != null ? ((java.sql.Date) r[5]).toLocalDate() : null;
                String status = calcularStatusCert(validade);
                lista.add(new RhDto.CertificacaoResponse(
                    ((Number) r[0]).intValue(), txt(r[1]), txt(r[2]), txt(r[3]),
                    r[4] != null ? ((java.sql.Date) r[4]).toLocalDate().format(D_FMT) : "",
                    validade != null ? validade.format(D_FMT) : "",
                    status, txt(r[6])));
            }
        } catch (Exception ignored) {}
        return lista;
    }

    // ===== helpers =====

    private String txt(Object v) { return v == null ? "" : String.valueOf(v); }

    private String calcularDuracao(java.time.LocalDateTime ini, java.time.LocalDateTime fim) {
        if (ini == null) return "";
        java.time.LocalDateTime base = fim != null ? fim : java.time.LocalDateTime.now();
        long minutos = java.time.Duration.between(ini, base).toMinutes();
        return (minutos / 60) + "h " + (minutos % 60) + "min";
    }

    private String calcularStatusCert(LocalDate validade) {
        if (validade == null) return "sem_validade";
        LocalDate hoje = LocalDate.now();
        if (validade.isBefore(hoje)) return "vencida";
        if (validade.isBefore(hoje.plusDays(30))) return "vencendo";
        return "valida";
    }
}
