package br.com.iv.repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import br.com.iv.entity.ConfiguracaoAgendaConsultorio;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfiguracaoAgendaConsultorioRepository implements PanacheRepository<ConfiguracaoAgendaConsultorio> {

    public List<ConfiguracaoAgendaConsultorio> listarPorUnidade(String unidade, boolean apenasAtivos) {
        if (apenasAtivos) {
            return find("upper(unidade) = ?1 and ativo = 'S' order by profissional, dataAgenda, diaSemana, horaInicio",
                    normalizarUnidade(unidade)).list();
        }
        return find("upper(unidade) = ?1 order by ativo desc, profissional, dataAgenda, diaSemana, horaInicio",
                normalizarUnidade(unidade)).list();
    }

    public Optional<ConfiguracaoAgendaConsultorio> buscarRegraAtiva(String unidade, String profissional,
            DayOfWeek diaSemana) {
        return find("upper(unidade) = ?1 and upper(profissional) = ?2 and diaSemana = ?3 and ativo = 'S'",
                normalizarUnidade(unidade), normalizarTexto(profissional), normalizarDiaSemana(diaSemana))
                .firstResultOptional();
    }

    private String normalizarUnidade(String unidade) {
        return String.valueOf(unidade == null ? "" : unidade).trim().toUpperCase();
    }

    private String normalizarTexto(String valor) {
        return String.valueOf(valor == null ? "" : valor).trim().toUpperCase();
    }

    private String normalizarDiaSemana(DayOfWeek diaSemana) {
        if (diaSemana == null) {
            return "";
        }
        switch (diaSemana) {
            case MONDAY:
                return "SEGUNDA";
            case TUESDAY:
                return "TERCA";
            case WEDNESDAY:
                return "QUARTA";
            case THURSDAY:
                return "QUINTA";
            case FRIDAY:
                return "SEXTA";
            case SATURDAY:
                return "SABADO";
            case SUNDAY:
                return "DOMINGO";
            default:
                return "";
        }
    }
}
