package br.com.iv.repository;

import java.util.ArrayList;
import java.util.List;

import br.com.iv.dto.PacientesImagensPacsDto;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class PacsRepository implements PanacheRepository<Object> {

    @Inject
    EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<PacientesImagensPacsDto> listarPorNumero(int numero, int first, int pageSize) {
        String sql = "SELECT i.idimagens, i.idbam, i.descricao, i.data, i.local, i.atendidopor, "
                + "p.nome AS nomePaciente, i.link, i.laudo "
                + "FROM imagens i "
                + "INNER JOIN pacientes p ON p.idpaciente = i.idpacientes "
                + "WHERE i.idbam = :numero "
                + "ORDER BY nomePaciente ASC, i.data DESC";

        var query = entityManager.createNativeQuery(sql);
        query.setParameter("numero", numero);
        query.setFirstResult(first);
        query.setMaxResults(pageSize);

        List<Object[]> results = query.getResultList();
        List<PacientesImagensPacsDto> list = new ArrayList<>();

        for (Object[] row : results) {
            PacientesImagensPacsDto dto = new PacientesImagensPacsDto();
            dto.setIdimagens(row[0] != null ? ((Number) row[0]).intValue() : 0);
            dto.setIdbam(row[1] != null ? ((Number) row[1]).intValue() : 0);
            dto.setDescricao((String) row[2]);
            dto.setData((String) row[3]);
            dto.setLocal((String) row[4]);
            dto.setAtendidopor((String) row[5]);
            dto.setNomePaciente((String) row[6]);
            dto.setLink((String) row[7]);
            dto.setLaudo((String) row[8]);
            list.add(dto);
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    public List<PacientesImagensPacsDto> listarPorNome(String nome, int first, int pageSize) {
        String sql = "SELECT i.idimagens, i.idbam, i.descricao, i.data, i.local, i.atendidopor, "
                + "p.nome AS nomePaciente, i.link, i.laudo "
                + "FROM imagens i "
                + "INNER JOIN pacientes p ON p.idpaciente = i.idpacientes "
                + "WHERE p.nome ILIKE :nome "
                + "ORDER BY nomePaciente ASC";

        var query = entityManager.createNativeQuery(sql);
        query.setParameter("nome", nome + "%");
        query.setFirstResult(first);
        query.setMaxResults(pageSize);

        List<Object[]> results = query.getResultList();
        List<PacientesImagensPacsDto> list = new ArrayList<>();

        for (Object[] row : results) {
            PacientesImagensPacsDto dto = new PacientesImagensPacsDto();
            dto.setIdimagens(row[0] != null ? ((Number) row[0]).intValue() : 0);
            dto.setIdbam(row[1] != null ? ((Number) row[1]).intValue() : 0);
            dto.setDescricao((String) row[2]);
            dto.setData((String) row[3]);
            dto.setLocal((String) row[4]);
            dto.setAtendidopor((String) row[5]);
            dto.setNomePaciente((String) row[6]);
            dto.setLink((String) row[7]);
            dto.setLaudo((String) row[8]);
            list.add(dto);
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    public List<PacientesImagensPacsDto> listarPorBam(int bam) {
        String sql = "SELECT i.idimagens, i.idbam, i.descricao, i.data, i.local, i.atendidopor, "
                + "p.nome AS nomePaciente, i.link, i.laudo "
                + "FROM imagens i "
                + "INNER JOIN pacientes p ON p.idpaciente = i.idpacientes "
                + "WHERE i.idbam = :bam "
                + "ORDER BY i.data DESC";

        var query = entityManager.createNativeQuery(sql);
        query.setParameter("bam", bam);

        List<Object[]> results = query.getResultList();
        List<PacientesImagensPacsDto> list = new ArrayList<>();

        for (Object[] row : results) {
            PacientesImagensPacsDto dto = new PacientesImagensPacsDto();
            dto.setIdimagens(row[0] != null ? ((Number) row[0]).intValue() : 0);
            dto.setIdbam(row[1] != null ? ((Number) row[1]).intValue() : 0);
            dto.setDescricao((String) row[2]);
            dto.setData((String) row[3]);
            dto.setLocal((String) row[4]);
            dto.setAtendidopor((String) row[5]);
            dto.setNomePaciente((String) row[6]);
            dto.setLink((String) row[7]);
            dto.setLaudo((String) row[8]);
            list.add(dto);
        }

        return list;
    }
}
