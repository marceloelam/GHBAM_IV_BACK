package br.com.iv.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.CirurgiaAgendamento;

@ApplicationScoped
public class CirurgiaAgendamentoRepository implements PanacheRepository<CirurgiaAgendamento> {

    public List<CirurgiaAgendamento> mapaDoDia(String unidade, LocalDateTime inicio, LocalDateTime fim, Integer idsala) {
        StringBuilder jpql = new StringBuilder("dataPrevista >= ?1 and dataPrevista <= ?2");
        List<Object> params = new ArrayList<>();
        params.add(inicio);
        params.add(fim);
        if (unidade != null && !unidade.isBlank()) {
            jpql.append(" and unidade = ?").append(params.size() + 1);
            params.add(unidade);
        }
        if (idsala != null) {
            jpql.append(" and idsala = ?").append(params.size() + 1);
            params.add(idsala);
        }
        return list(jpql.toString(), Sort.by("dataPrevista").ascending(), params.toArray());
    }

    public List<CirurgiaAgendamento> listarFiltrado(String unidade, String status, String especialidade, String busca,
            int pagina, int tamanho) {
        StringBuilder jpql = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();
        if (unidade != null && !unidade.isBlank()) {
            jpql.append(" and unidade = ?").append(params.size() + 1);
            params.add(unidade);
        }
        if (status != null && !status.isBlank()) {
            jpql.append(" and status = ?").append(params.size() + 1);
            params.add(status);
        }
        if (especialidade != null && !especialidade.isBlank()) {
            jpql.append(" and especialidade = ?").append(params.size() + 1);
            params.add(especialidade);
        }
        if (busca != null && !busca.isBlank()) {
            jpql.append(" and (lower(nomepaciente) like ?").append(params.size() + 1)
                    .append(" or numero like ?").append(params.size() + 2).append(")");
            params.add("%" + busca.toLowerCase() + "%");
            params.add("%" + busca + "%");
        }
        return find(jpql.toString(), Sort.by("dataPrevista").descending(), params.toArray())
                .page(pagina, tamanho).list();
    }

    public List<CirurgiaAgendamento> conflitosDeSala(int idsala, LocalDateTime inicio, LocalDateTime fim,
            Integer idcirurgiaIgnorar) {
        List<CirurgiaAgendamento> candidatas = list("idsala = ?1 and status not in ('SUSPENSA', 'CANCELADA')", idsala);
        List<CirurgiaAgendamento> conflitos = new ArrayList<>();
        for (CirurgiaAgendamento c : candidatas) {
            if (idcirurgiaIgnorar != null && idcirurgiaIgnorar.equals(c.getIdcirurgia())) {
                continue;
            }
            LocalDateTime cIni = c.getDataPrevista();
            LocalDateTime cFim = cIni.plusMinutes(c.getDuracaoPrevistaMin());
            if (cIni.isBefore(fim) && cFim.isAfter(inicio)) {
                conflitos.add(c);
            }
        }
        return conflitos;
    }

    public List<CirurgiaAgendamento> porPaciente(int idpaciente) {
        return list("idpaciente", Sort.by("dataPrevista").descending(), idpaciente);
    }

    public long contarPorStatus(String unidade, String status) {
        if (unidade == null || unidade.isBlank()) {
            return count("status", status);
        }
        return count("unidade = ?1 and status = ?2", unidade, status);
    }
}
