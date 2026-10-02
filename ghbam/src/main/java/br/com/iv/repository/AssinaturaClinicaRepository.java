package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.AssinaturaClinica;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AssinaturaClinicaRepository implements PanacheRepositoryBase<AssinaturaClinica, Integer> {

    public List<AssinaturaClinica> listarPorDocumento(String tipoDocumento, Integer iddocumento) {
        return list("tipoDocumento = ?1 and iddocumento = ?2 order by coalesce(adendoEm, assinadoEm) desc",
                tipoDocumento, iddocumento);
    }

    public List<AssinaturaClinica> listarPorBam(String tipoDocumento, Integer idbam) {
        return list("tipoDocumento = ?1 and idbam = ?2 order by coalesce(adendoEm, assinadoEm) desc",
                tipoDocumento, idbam);
    }

    public List<AssinaturaClinica> listarPorBam(Integer idbam) {
        return list("idbam = ?1 order by tipoDocumento asc, coalesce(adendoEm, assinadoEm) desc", idbam);
    }

    public AssinaturaClinica assinaturaAtivaPorBam(String tipoDocumento, Integer idbam) {
        return find("tipoDocumento = ?1 and idbam = ?2 and status = 'ASSINADO' order by assinadoEm desc",
                tipoDocumento, idbam).firstResult();
    }

    public AssinaturaClinica assinaturaAtivaPorDocumento(String tipoDocumento, Integer iddocumento) {
        return find("tipoDocumento = ?1 and iddocumento = ?2 and status = 'ASSINADO' order by assinadoEm desc",
                tipoDocumento, iddocumento).firstResult();
    }
}
