package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.PrescricaoItemCicloEvento;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PrescricaoItemCicloEventoRepository implements PanacheRepositoryBase<PrescricaoItemCicloEvento, Integer> {

    public List<PrescricaoItemCicloEvento> listarPorItem(int iditem) {
        return find("iditem = ?1 order by criadoEm desc, idevento desc", iditem).list();
    }

    public List<PrescricaoItemCicloEvento> listarPorBam(int idbam) {
        return find("idbam = ?1 order by criadoEm desc, idevento desc", idbam).list();
    }
}
