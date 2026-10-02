package br.com.iv.repository;

import br.com.iv.entity.DocumentoEmbedding;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;

import java.util.List;

@ApplicationScoped
public class DocumentoEmbeddingRepository implements PanacheRepository<DocumentoEmbedding> {

    private String colunas = "idragdocumento, titulo, conteudo, tipo, setor, idpaciente, idprontuario, criadoem, atualizadoem, 1 - (embedding <=> CAST(?1 AS vector)) AS similaridade";

    public List<Object[]> buscarPorSemelhanca(String embeddingStr, int limite) {
        Query query = getEntityManager().createNativeQuery(
            "SELECT " + colunas + " FROM rag_documentos WHERE embedding IS NOT NULL " +
            "ORDER BY embedding <=> CAST(?1 AS vector) LIMIT ?2"
        );
        query.setParameter(1, embeddingStr);
        query.setParameter(2, limite);
        return query.getResultList();
    }

    public List<Object[]> buscarPorSemelhancaESetor(String embeddingStr, String setor, int limite) {
        Query query = getEntityManager().createNativeQuery(
            "SELECT " + colunas + " FROM rag_documentos WHERE embedding IS NOT NULL AND setor = ?2 " +
            "ORDER BY embedding <=> CAST(?1 AS vector) LIMIT ?3"
        );
        query.setParameter(1, embeddingStr);
        query.setParameter(2, setor);
        query.setParameter(3, limite);
        return query.getResultList();
    }

    public List<Object[]> buscarPorSemelhancaEPaciente(String embeddingStr, int idpaciente, int limite) {
        Query query = getEntityManager().createNativeQuery(
            "SELECT " + colunas + " FROM rag_documentos WHERE embedding IS NOT NULL AND idpaciente = ?2 " +
            "ORDER BY embedding <=> CAST(?1 AS vector) LIMIT ?3"
        );
        query.setParameter(1, embeddingStr);
        query.setParameter(2, idpaciente);
        query.setParameter(3, limite);
        return query.getResultList();
    }

    public void atualizarEmbedding(int idDocumento, String embeddingStr) {
        Query query = getEntityManager().createNativeQuery(
            "UPDATE rag_documentos SET embedding = CAST(?1 AS vector) WHERE idragdocumento = ?2"
        );
        query.setParameter(1, embeddingStr);
        query.setParameter(2, idDocumento);
        query.executeUpdate();
    }

    public List<Object[]> buscarPorPalavraChave(String termo, int limite) {
        Query query = getEntityManager().createNativeQuery(
            "SELECT idragdocumento, titulo, conteudo, tipo, setor, idpaciente, idprontuario, criadoem, atualizadoem, 0 AS similaridade " +
            "FROM rag_documentos WHERE conteudo ILIKE ?1 OR titulo ILIKE ?1 " +
            "ORDER BY criadoem DESC LIMIT ?2"
        );
        query.setParameter(1, "%" + termo + "%");
        query.setParameter(2, limite);
        return query.getResultList();
    }

    public List<Object[]> buscarPorPalavraChaveEPaciente(String termo, int idpaciente, int limite) {
        Query query = getEntityManager().createNativeQuery(
            "SELECT idragdocumento, titulo, conteudo, tipo, setor, idpaciente, idprontuario, criadoem, atualizadoem, 0 AS similaridade " +
            "FROM rag_documentos WHERE (conteudo ILIKE ?1 OR titulo ILIKE ?1) AND idpaciente = ?2 " +
            "ORDER BY criadoem DESC LIMIT ?3"
        );
        query.setParameter(1, "%" + termo + "%");
        query.setParameter(2, idpaciente);
        query.setParameter(3, limite);
        return query.getResultList();
    }

    public List<DocumentoEmbedding> buscarPorTipo(String tipo) {
        return find("tipo", tipo).list();
    }

    public List<DocumentoEmbedding> buscarPorSetor(String setor) {
        return find("setor", setor).list();
    }

    public long contarPorTipo(String tipo) {
        return count("tipo", tipo);
    }
}
