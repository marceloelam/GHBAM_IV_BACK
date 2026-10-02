package br.com.iv.service;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import br.com.iv.dto.PharmaciaProdutoDTO;
import br.com.iv.entity.PharmaciaCategoria;
import br.com.iv.entity.PharmaciaProduto;

@ApplicationScoped
public class PharmaciaProdutoService {

    @Inject
    EntityManager em;

    public List<PharmaciaProduto> listAll() {
        return PharmaciaProduto.listAll();
    }

    public PharmaciaProduto findById(Long id) {
        return PharmaciaProduto.findById(id);
    }

    @Transactional
    public PharmaciaProduto saveFromDTO(PharmaciaProdutoDTO dto) {
        PharmaciaProduto produto = new PharmaciaProduto();
        produto.setId(dto.getId());
        produto.setNomeComercial(dto.getNomeComercial());
        produto.setNomeGenerico(dto.getNomeGenerico());
        String cb = dto.getCodigoBarras();
        produto.setCodigoBarras(cb != null && !cb.trim().isEmpty() ? cb.trim() : null);
        produto.setFormaFarmaceutica(dto.getFormaFarmaceutica());
        produto.setUnidadeMedida(dto.getUnidadeMedida());
        produto.setFabricante(dto.getFabricante());
        produto.setRegistroAnvisa(dto.getRegistroAnvisa());
        produto.setAtivo(dto.getAtivo() != null ? dto.getAtivo() : true);
        produto.setUnidadeSigla(dto.getUnidadeSigla());
        // Campos clinicos (V158): sem eles a validacao farmaceutica nao reconhece o item.
        produto.setPrincipioAtivo(dto.getPrincipioAtivo());
        produto.setConcentracao(dto.getConcentracao());
        produto.setViaAdministracao(dto.getViaAdministracao());
        produto.setCodigoAtc(dto.getCodigoAtc());
        produto.setAltaVigilancia(dto.getAltaVigilancia());
        produto.setAntimicrobiano(dto.getAntimicrobiano());
        produto.setListaControlada(dto.getListaControlada());
        produto.setExigeReceita(dto.getExigeReceita());

        if (dto.getCategoriaId() != null) {
            PharmaciaCategoria cat = PharmaciaCategoria.findById(dto.getCategoriaId());
            produto.setCategoria(cat);
        }

        return save(produto);
    }

    @Transactional
    public PharmaciaProduto save(PharmaciaProduto produto) {
        if (produto.getId() == null) {
            // NOVO: Usar merge que lida melhor com instâncias detached
            // Preparar referências gerenciadas
            if (produto.getCategoria() != null && produto.getCategoria().getId() != null) {
                PharmaciaCategoria catRef = em.getReference(PharmaciaCategoria.class, produto.getCategoria().getId());
                produto.setCategoria(catRef);
            } else {
                produto.setCategoria(null);
            }
            PharmaciaProduto merged = em.merge(produto);
            merged.setUnidadeSigla(produto.getUnidadeSigla());
            return merged;
        } else {
            // ATUALIZAR: Usar update com IDs para evitar problemas de cascade
            Long categoriaId = produto.getCategoria() != null ? produto.getCategoria().getId() : null;

            String updateQuery;
            Object[] params;
            // Campos clinicos (?10 a ?17) entram no update junto com os demais:
            // se ficarem de fora, editar um produto apaga o principio ativo e
            // desliga silenciosamente a validacao farmaceutica daquele item.
            String camposClinicos = "principioAtivo = ?10, concentracao = ?11, viaAdministracao = ?12, "
                + "codigoAtc = ?13, altaVigilancia = ?14, antimicrobiano = ?15, listaControlada = ?16, exigeReceita = ?17";

            if (categoriaId != null) {
                updateQuery = "nomeComercial = ?1, nomeGenerico = ?2, codigoBarras = ?3, formaFarmaceutica = ?4, " +
                    "unidadeMedida = ?5, fabricante = ?6, registroAnvisa = ?7, ativo = ?8, unidadeSigla = ?9, " +
                    camposClinicos + ", categoria = ?18 where id = ?19";
                params = new Object[]{
                    produto.getNomeComercial(), produto.getNomeGenerico(), produto.getCodigoBarras(),
                    produto.getFormaFarmaceutica(), produto.getUnidadeMedida(), produto.getFabricante(),
                    produto.getRegistroAnvisa(), produto.getAtivo(), produto.getUnidadeSigla(),
                    produto.getPrincipioAtivo(), produto.getConcentracao(), produto.getViaAdministracao(),
                    produto.getCodigoAtc(), produto.getAltaVigilancia(), produto.getAntimicrobiano(),
                    produto.getListaControlada(), produto.getExigeReceita(),
                    PharmaciaCategoria.findById(categoriaId), produto.getId()
                };
            } else {
                updateQuery = "nomeComercial = ?1, nomeGenerico = ?2, codigoBarras = ?3, formaFarmaceutica = ?4, " +
                    "unidadeMedida = ?5, fabricante = ?6, registroAnvisa = ?7, ativo = ?8, unidadeSigla = ?9, " +
                    camposClinicos + " where id = ?18";
                params = new Object[]{
                    produto.getNomeComercial(), produto.getNomeGenerico(), produto.getCodigoBarras(),
                    produto.getFormaFarmaceutica(), produto.getUnidadeMedida(), produto.getFabricante(),
                    produto.getRegistroAnvisa(), produto.getAtivo(), produto.getUnidadeSigla(),
                    produto.getPrincipioAtivo(), produto.getConcentracao(), produto.getViaAdministracao(),
                    produto.getCodigoAtc(), produto.getAltaVigilancia(), produto.getAntimicrobiano(),
                    produto.getListaControlada(), produto.getExigeReceita(), produto.getId()
                };
            }
            long updated = PharmaciaProduto.update(updateQuery, params);
            if (updated == 0) {
                throw new RuntimeException("Produto não encontrado com id: " + produto.getId());
            }
            return PharmaciaProduto.findById(produto.getId());
        }
    }

    @Transactional
    public void delete(Long id) {
        PharmaciaProduto.deleteById(id);
    }

    public List<PharmaciaProduto> findByAtivo(Boolean ativo) {
        return PharmaciaProduto.list("ativo", ativo);
    }

    public List<PharmaciaProduto> findByCategoria(Long categoriaId) {
        return PharmaciaProduto.list("categoria.id", categoriaId);
    }

    public List<PharmaciaProduto> findByUnidadeSigla(String sigla) {
        return PharmaciaProduto.list("unidadeSigla", sigla);
    }
}
