package br.com.iv.service;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaCategoria;

@ApplicationScoped
public class PharmaciaCategoriaService {

    public List<PharmaciaCategoria> listAll() {
        return PharmaciaCategoria.listAll();
    }

    public PharmaciaCategoria findById(Long id) {
        return PharmaciaCategoria.findById(id);
    }

    @Transactional
    public PharmaciaCategoria save(PharmaciaCategoria categoria) {
        if (categoria.getId() == null) {
            categoria.persist();
            return categoria;
        } else {
            PharmaciaCategoria existing = PharmaciaCategoria.findById(categoria.getId());
            if (existing == null) throw new RuntimeException("Categoria não encontrada com id: " + categoria.getId());
            existing.setNome(categoria.getNome());
            existing.setDescricao(categoria.getDescricao());
            existing.setAtivo(categoria.getAtivo());
            existing.setUnidadeSigla(categoria.getUnidadeSigla());
            existing.persist();
            return existing;
        }
    }

    @Transactional
    public void delete(Long id) {
        PharmaciaCategoria.deleteById(id);
    }

    @Transactional
    public void desativar(Long id) {
        PharmaciaCategoria categoria = PharmaciaCategoria.findById(id);
        if (categoria != null) {
            categoria.setAtivo(false);
            categoria.persist();
        }
    }

    public List<PharmaciaCategoria> findByAtivo(Boolean ativo) {
        return PharmaciaCategoria.list("ativo", ativo);
    }

    public List<PharmaciaCategoria> findByUnidadeSigla(String sigla) {
        return PharmaciaCategoria.list("unidadeSigla", sigla);
    }
}
