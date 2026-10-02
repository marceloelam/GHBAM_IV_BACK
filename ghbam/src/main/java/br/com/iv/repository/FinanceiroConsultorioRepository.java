package br.com.iv.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.com.iv.entity.FinanceiroConsultorio;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FinanceiroConsultorioRepository implements PanacheRepository<FinanceiroConsultorio> {

    public List<FinanceiroConsultorio> listarPorPeriodoUnidade(LocalDate dataInicio, LocalDate dataFim, String unidade) {
        return find("dataAtendimento between ?1 and ?2 and upper(unidade) = ?3 order by dataAtendimento desc, idfinanceiroconsultorio desc",
                dataInicio, dataFim, normalizarUnidade(unidade)).list();
    }

    public List<FinanceiroConsultorio> listarPorPaciente(int idpaciente, String unidade) {
        return find("idpaciente = ?1 and upper(unidade) = ?2 order by dataAtendimento desc, idfinanceiroconsultorio desc",
                idpaciente, normalizarUnidade(unidade)).list();
    }

    public Optional<FinanceiroConsultorio> buscarPorAgendamento(int idescalaPaciente, String unidade) {
        if (idescalaPaciente <= 0) {
            return Optional.empty();
        }
        return find("idescalaPaciente = ?1 and upper(unidade) = ?2 order by idfinanceiroconsultorio desc",
                idescalaPaciente, normalizarUnidade(unidade)).firstResultOptional();
    }

    private String normalizarUnidade(String unidade) {
        return String.valueOf(unidade == null ? "" : unidade).trim().toUpperCase();
    }
}
