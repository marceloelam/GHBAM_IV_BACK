package br.com.iv.repository;

import java.time.LocalDate;
import java.util.List;

import br.com.iv.entity.NspEventoAcao;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NspEventoAcaoRepository implements PanacheRepositoryBase<NspEventoAcao, Long> {

    public List<NspEventoAcao> listarDoEvento(Long idevento) {
        return find("idevento = ?1 order by criadoEm asc", idevento).list();
    }

    public long contarPendentes(Long idevento) {
        return count("idevento = ?1 and tipo in (?2) and status = ?3",
                idevento, List.of("ACAO_CORRETIVA", "ACAO_PREVENTIVA"), "PENDENTE");
    }

    public long contarAcoes(Long idevento) {
        return count("idevento = ?1 and tipo in (?2)", idevento, List.of("ACAO_CORRETIVA", "ACAO_PREVENTIVA"));
    }

    public List<NspEventoAcao> listarVencidas(List<Long> eventos, LocalDate referencia) {
        if (eventos == null || eventos.isEmpty()) {
            return List.of();
        }
        return find("idevento in ?1 and status = ?2 and prazo is not null and prazo < ?3 order by prazo asc",
                eventos, "PENDENTE", referencia).list();
    }
}
