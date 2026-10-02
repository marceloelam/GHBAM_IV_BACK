package br.com.iv.repository;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.FaturamentoRaas;

@ApplicationScoped
public class FaturamentoRaasRepository implements PanacheRepository<FaturamentoRaas> {

    public List<FaturamentoRaas> listar(String status, Integer idcompetencia) {
        if (status != null && !status.isBlank() && idcompetencia != null) {
            return list("status = ?1 and idcompetencia = ?2", Sort.by("criadoEm").descending(), status, idcompetencia);
        }
        if (status != null && !status.isBlank()) {
            return list("status", Sort.by("criadoEm").descending(), status);
        }
        if (idcompetencia != null) {
            return list("idcompetencia", Sort.by("criadoEm").descending(), idcompetencia);
        }
        return listAll(Sort.by("criadoEm").descending());
    }

    public long contarPorCompetencia(int idcompetencia) {
        return count("idcompetencia", idcompetencia);
    }

    public long contarPorCompetenciaUnidade(Integer idcompetencia, String unidade) {
        if (idcompetencia == null) {
            if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)) {
                return count("upper(unidade) = ?1", unidade.trim().toUpperCase());
            }
            return count();
        }
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)) {
            return count("idcompetencia = ?1 and upper(unidade) = ?2", idcompetencia, unidade.trim().toUpperCase());
        }
        return count("idcompetencia", idcompetencia);
    }

    public boolean existeParaPacienteProcedimento(Integer idcompetencia, String unidade, int idpaciente, String procedimento) {
        return count("idcompetencia = ?1 and upper(unidade) = ?2 and idpaciente = ?3 and procedimento = ?4",
                idcompetencia, unidadeNormalizada(unidade), idpaciente, procedimento) > 0;
    }

    public List<FaturamentoRaas> listarElegiveisExportacao(int idcompetencia, String unidade) {
        return find("idcompetencia = ?1 and upper(unidade) = ?2 and status = ?3 and (exportado is null or exportado = false)",
                Sort.by("criadoEm").ascending(), idcompetencia, unidadeNormalizada(unidade), "CONCLUIDA").list();
    }

    public long contarElegiveisExportacao(int idcompetencia, String unidade) {
        return count("idcompetencia = ?1 and upper(unidade) = ?2 and status = ?3 and (exportado is null or exportado = false)",
                idcompetencia, unidadeNormalizada(unidade), "CONCLUIDA");
    }

    public long contarStatusPorCompetenciaUnidade(int idcompetencia, String unidade, String status) {
        return count("idcompetencia = ?1 and upper(unidade) = ?2 and status = ?3",
                idcompetencia, unidadeNormalizada(unidade), status);
    }

    // Devolve para a fila de exportacao as RAAS que sairam numa remessa cancelada.
    public long reverterExportacaoPorRemessa(String numeroRemessa) {
        return update("exportado = false, dataExportacao = null, numeroRemessa = null where numeroRemessa = ?1",
                numeroRemessa);
    }

    public long contarExportadosPorCompetenciaUnidade(int idcompetencia, String unidade) {
        return count("idcompetencia = ?1 and upper(unidade) = ?2 and exportado = true",
                idcompetencia, unidadeNormalizada(unidade));
    }

    public List<FaturamentoRaas> listarPaginado(String status, Integer idcompetencia, String unidade, String busca,
            int pagina, int tamanho) {
        var filtro = filtro(status, idcompetencia, unidade, busca);
        int tam = tamanho <= 0 || tamanho > 200 ? 50 : tamanho;
        return find(filtro.jpql(), Sort.by("criadoEm").descending(), filtro.params())
                .page(Math.max(pagina, 0), tam)
                .list();
    }

    public long contarFiltrado(String status, Integer idcompetencia, String unidade, String busca) {
        var filtro = filtro(status, idcompetencia, unidade, busca);
        return count(filtro.jpql(), filtro.params());
    }

    private Filtro filtro(String status, Integer idcompetencia, String unidade, String busca) {
        StringBuilder jpql = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();
        if (status != null && !status.isBlank()) {
            jpql.append(" and status = :status");
            params.put("status", status.trim().toUpperCase());
        }
        if (idcompetencia != null) {
            jpql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)) {
            jpql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        if (busca != null && !busca.isBlank()) {
            jpql.append(" and (upper(nomepaciente) like :busca or upper(numero) like :busca or upper(procedimento) like :busca)");
            params.put("busca", "%" + busca.trim().toUpperCase() + "%");
        }
        return new Filtro(jpql.toString(), params);
    }

    private String unidadeNormalizada(String unidade) {
        return unidade == null ? "" : unidade.trim().toUpperCase();
    }

    private record Filtro(String jpql, Map<String, Object> params) {
    }
}
