package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.PrescricaoItemEstruturado;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PrescricaoItemEstruturadoRepository implements PanacheRepositoryBase<PrescricaoItemEstruturado, Integer> {

    public List<PrescricaoItemEstruturado> listarPorBam(int idbam) {
        return find("idbam = ?1 order by iditem desc", idbam).list();
    }

    public List<PrescricaoItemEstruturado> listarAtivosPorBam(int idbam) {
        return find("idbam = ?1 and status = 'ATIVO' order by iditem desc", idbam).list();
    }

    public List<PrescricaoItemEstruturado> listarPorPrescricao(int idprescricao) {
        return find("idprescricao = ?1 order by iditem desc", idprescricao).list();
    }
}
