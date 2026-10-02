package br.com.iv.service;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaUnidade;

@ApplicationScoped
public class PharmaciaUnidadeService {

    public List<PharmaciaUnidade> listAll() {
        return PharmaciaUnidade.listAll();
    }

    public PharmaciaUnidade findById(Long id) {
        return PharmaciaUnidade.findById(id);
    }

    @Transactional
    public PharmaciaUnidade save(PharmaciaUnidade unidade) {
        if (unidade.getId() == null) {
            unidade.persist();
        } else {
            PharmaciaUnidade existing = PharmaciaUnidade.findById(unidade.getId());
            if (existing != null) {
                existing.setNome(unidade.getNome());
                existing.setSigla(unidade.getSigla());
                existing.setEndereco(unidade.getEndereco());
                existing.setAtivo(unidade.getAtivo());
            }
        }
        return unidade;
    }

    @Transactional
    public void delete(Long id) {
        PharmaciaUnidade.deleteById(id);
    }

    public List<PharmaciaUnidade> findByAtivo(Boolean ativo) {
        return PharmaciaUnidade.list("ativo", ativo);
    }

    public PharmaciaUnidade findBySigla(String sigla) {
        return PharmaciaUnidade.find("sigla", sigla).firstResult();
    }
}
