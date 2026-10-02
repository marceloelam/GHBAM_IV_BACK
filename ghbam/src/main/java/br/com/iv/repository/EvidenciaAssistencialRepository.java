package br.com.iv.repository;

import br.com.iv.entity.EvidenciaAssistencial;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EvidenciaAssistencialRepository implements PanacheRepositoryBase<EvidenciaAssistencial, Long> {

    public List<EvidenciaAssistencial> listar(String modulo, String unidade, String status, String origemModulo, String origemId) {
        StringBuilder jpql = new StringBuilder("modulo = :modulo");
        Map<String, Object> params = new HashMap<>();
        params.put("modulo", modulo);

        aplicarFiltros(jpql, params, unidade, status, origemModulo, origemId);

        return find(jpql + " order by dataRegistro desc", params).page(0, 200).list();
    }

    public long contar(String modulo, String unidade, String status, String origemModulo, String origemId) {
        StringBuilder jpql = new StringBuilder("modulo = :modulo");
        Map<String, Object> params = new HashMap<>();
        params.put("modulo", modulo);

        aplicarFiltros(jpql, params, unidade, status, origemModulo, origemId);

        return count(jpql.toString(), params);
    }

    private void aplicarFiltros(StringBuilder jpql, Map<String, Object> params, String unidade, String status, String origemModulo, String origemId) {
        if (unidade != null && !unidade.isBlank()) {
            jpql.append(" and unidade = :unidade");
            params.put("unidade", unidade);
        }
        if (status != null && !status.isBlank()) {
            jpql.append(" and status = :status");
            params.put("status", status);
        }
        if (origemModulo != null && !origemModulo.isBlank()) {
            jpql.append(" and origemModulo = :origemModulo");
            params.put("origemModulo", origemModulo);
        }
        if (origemId != null && !origemId.isBlank()) {
            jpql.append(" and origemId = :origemId");
            params.put("origemId", origemId);
        }
    }

    public EvidenciaAssistencial buscarDoModulo(Long id, String modulo) {
        if (id == null || modulo == null || modulo.isBlank()) {
            return null;
        }
        return find("idevidencia = ?1 and modulo = ?2", id, modulo).firstResult();
    }

    public EvidenciaAssistencial buscarPorOrigemEModulo(String origemModulo, String origemId, String modulo) {
        return find("origemModulo = ?1 and origemId = ?2 and modulo = ?3", origemModulo, origemId, modulo).firstResult();
    }

    public List<EvidenciaAssistencial> listarPorOrigem(String origemModulo, String origemId) {
        return find("origemModulo = ?1 and origemId = ?2 order by dataRegistro asc", origemModulo, origemId).list();
    }
}
