package br.com.iv.repository;

import java.util.List;
import java.util.Optional;

import br.com.iv.entity.PrescricaoAprazamentoChecagem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PrescricaoAprazamentoChecagemRepository implements PanacheRepositoryBase<PrescricaoAprazamentoChecagem, Integer> {

    public List<PrescricaoAprazamentoChecagem> listarPorPrescricao(int idprescricao) {
        return list("idprescricao = ?1 order by indiceHorario asc", idprescricao);
    }

    public Optional<PrescricaoAprazamentoChecagem> buscarHorario(int idprescricao, int indiceHorario) {
        return find("idprescricao = ?1 and indiceHorario = ?2", idprescricao, indiceHorario).firstResultOptional();
    }
}
