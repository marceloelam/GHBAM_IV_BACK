package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.AnotacaoEnfermagem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AnotacaoEnfermagemRepository implements PanacheRepositoryBase<AnotacaoEnfermagem, Integer> {

    public List<AnotacaoEnfermagem> listarPorPaciente(int idpaciente) {
        return find("idpaciente = ?1 and status = 'ATIVA' order by criadoEm desc", idpaciente).list();
    }

    public List<AnotacaoEnfermagem> listarPorBam(int idbam) {
        return find("idbam = ?1 and status = 'ATIVA' order by criadoEm desc", idbam).list();
    }
}
