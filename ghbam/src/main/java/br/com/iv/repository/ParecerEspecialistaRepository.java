package br.com.iv.repository;

import br.com.iv.entity.ParecerEspecialista;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import java.util.List;
import br.com.iv.dto.AnamneseInternacaoDto;

@ApplicationScoped
public class ParecerEspecialistaRepository implements PanacheRepositoryBase<ParecerEspecialista, Integer> {
    @Inject
    EntityManager em;

    public List<AnamneseInternacaoDto> findAnamneseInternacao(int nbam, String unidade) {
    // Seleciona colunas específicas para evitar ambiguidade e facilitar o mapeamento
    // notas: prontuario, medico e crm pertencem à tabela anamneseinternacao (alias a)
    String sql = "SELECT a.frente, a.data, a.verso, a.prontuario, a.medico, a.crm, a.nbam " +
        "FROM anamneseinternacao a " +
        "INNER JOIN bam b ON a.nbam = b.idbam " +
        "WHERE a.nbam = :nbam AND b.unidade = :unidade";

    @SuppressWarnings("unchecked")
    List<Object[]> results = em.createNativeQuery(sql)
        .setParameter("nbam", nbam)
        .setParameter("unidade", unidade)
        .getResultList();

    // Log temporário para depuração: quantos resultados vieram
    System.out.println("[DEBUG] Executando findAnamneseInternacao nbam=" + nbam + " unidade=" + unidade);
    // Mapear cada linha (Object[]) para AnamneseInternacaoDto
    return results.stream().map(row -> {
        AnamneseInternacaoDto dto = new AnamneseInternacaoDto();
        // ordem: frente, data, verso, prontuario, medico, crm, nbam
        dto.setFrente(row[0] != null ? row[0].toString() : null);
        dto.setData(row[1] instanceof java.sql.Date ? new java.util.Date(((java.sql.Date) row[1]).getTime()) : (java.util.Date) row[1]);
        dto.setVerso(row[2] != null ? row[2].toString() : null);
        dto.setProntuario(row[3] != null ? row[3].toString() : null);
        dto.setMedico(row[4] != null ? row[4].toString() : null);
        dto.setCrm(row[5] != null ? row[5].toString() : null);
        dto.setNbam(row[6] != null ? ((Number) row[6]).intValue() : 0);
        return dto;
    }).collect(java.util.stream.Collectors.toList());
    }
    
    public List<ParecerEspecialista> findParecerEspecialistaByNbamAndUnidade(int nbam, String unidade) {
    // entidade declarada com @Entity(name = "parecerespecialista")
    String jpql = "SELECT p FROM parecerespecialista p WHERE p.nbam = :nbam AND p.unidade = :unidade";
    return em.createQuery(jpql, ParecerEspecialista.class)
        .setParameter("nbam", nbam)
        .setParameter("unidade", unidade)
        .getResultList();
    }

    public ParecerEspecialista findUltimoParecerEspecialistaByNbamAndUnidade(int nbam, String unidade) {
    // usar o nome da entidade "parecerespecialista" conforme anotação @Entity
    String jpql = "SELECT p FROM parecerespecialista p WHERE p.nbam = :nbam AND p.unidade = :unidade ORDER BY p.idparecerespecialista DESC";
    List<ParecerEspecialista> result = em.createQuery(jpql, ParecerEspecialista.class)
        .setParameter("nbam", nbam)
        .setParameter("unidade", unidade)
        .setMaxResults(1)
        .getResultList();
        return result.isEmpty() ? null : result.get(0);
    }
    
    public List<ParecerEspecialista> listaDePareceres(String especialidade, String unidade) {
        String jpql = "SELECT p FROM parecerespecialista p WHERE p.especialidadedestino = :especialidade AND p.unidade = :unidade AND (p.desc2 = '' OR p.desc2 IS NULL) ORDER BY p.idparecerespecialista DESC";
        return em.createQuery(jpql, ParecerEspecialista.class)
            .setParameter("especialidade", especialidade)
            .setParameter("unidade", unidade)
            .getResultList();
    }
}