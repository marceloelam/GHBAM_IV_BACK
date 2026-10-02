package br.com.iv.cdss.repository;

import java.util.List;

import br.com.iv.cdss.entity.CdssAlertaOverride;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CdssAlertaOverrideRepository implements PanacheRepositoryBase<CdssAlertaOverride, Integer> {

    public List<CdssAlertaOverride> listarPorBam(int idbam) {
        return find("idbam = ?1 order by criadoEm desc", idbam).list();
    }

    public List<CdssAlertaOverride> listarPorPaciente(int idpaciente) {
        return find("idpaciente = ?1 order by criadoEm desc", idpaciente).list();
    }
}
