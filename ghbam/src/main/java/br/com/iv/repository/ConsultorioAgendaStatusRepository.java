package br.com.iv.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.com.iv.entity.ConsultorioAgendaStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConsultorioAgendaStatusRepository implements PanacheRepository<ConsultorioAgendaStatus> {

    public List<ConsultorioAgendaStatus> listarPorDataUnidade(LocalDate data, String unidade) {
        return find("dataAtendimento = ?1 and upper(unidade) = ?2 order by idescalaPaciente",
                data, normalizarUnidade(unidade)).list();
    }

    public Optional<ConsultorioAgendaStatus> buscarPorAgendamento(int idescalaPaciente, String unidade) {
        if (idescalaPaciente <= 0) {
            return Optional.empty();
        }
        return find("idescalaPaciente = ?1 and upper(unidade) = ?2", idescalaPaciente, normalizarUnidade(unidade))
                .firstResultOptional();
    }

    public long contarEncaixes(LocalDate data, String unidade, String profissional, int ignorarIdescalaPaciente) {
        return count("dataAtendimento = ?1 and upper(unidade) = ?2 and upper(profissional) = ?3 and status = 'ENCAIXE' and idescalaPaciente <> ?4",
                data, normalizarUnidade(unidade), normalizarTexto(profissional), ignorarIdescalaPaciente);
    }

    private String normalizarUnidade(String unidade) {
        return String.valueOf(unidade == null ? "" : unidade).trim().toUpperCase();
    }

    private String normalizarTexto(String valor) {
        return String.valueOf(valor == null ? "" : valor).trim().toUpperCase();
    }
}
