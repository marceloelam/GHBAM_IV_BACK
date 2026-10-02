package br.com.iv.repository;

import java.time.LocalDate;
import java.util.List;

import br.com.iv.entity.SaeEnfermagem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SaeEnfermagemRepository implements PanacheRepositoryBase<SaeEnfermagem, Integer> {

    public SaeEnfermagem buscarPorUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return find("uuid", uuid.trim()).firstResult();
    }

    public SaeEnfermagem buscarRegistro(Integer idbam, Integer idpaciente, LocalDate data, String tipoSae) {
        if (data == null) {
            return null;
        }
        if (idbam != null && idbam > 0) {
            return find("idbam = ?1 and data = ?2 and tipoSae = ?3 order by versao desc, atualizadoEm desc", idbam, data, tipoSae).firstResult();
        }
        if (idpaciente != null && idpaciente > 0) {
            return find("idpaciente = ?1 and data = ?2 and tipoSae = ?3 order by versao desc, atualizadoEm desc", idpaciente, data, tipoSae).firstResult();
        }
        return null;
    }

    public List<SaeEnfermagem> listarHistorico(Integer idbam, Integer idpaciente) {
        if (idbam != null && idbam > 0) {
            return find("idbam = ?1 order by data desc, atualizadoEm desc", idbam).list();
        }
        if (idpaciente != null && idpaciente > 0) {
            return find("idpaciente = ?1 order by data desc, atualizadoEm desc", idpaciente).list();
        }
        return List.of();
    }
}
