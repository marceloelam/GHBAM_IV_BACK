package br.com.iv.repository;

import br.com.iv.entity.RagAprendizado;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;

import java.util.List;

@ApplicationScoped
public class RagAprendizadoRepository implements PanacheRepository<RagAprendizado> {

    public List<Object[]> buscarPorSemelhanca(String embeddingStr, int limite) {
        Query query = getEntityManager().createNativeQuery(
            "SELECT idaprendizado, pergunta, resposta, contexto_resumido, usou_web, idpaciente, feedback, usos, " +
            "1 - (embed_pergunta <=> ?1) AS similaridade " +
            "FROM rag_aprendizado WHERE embed_pergunta IS NOT NULL AND feedback >= 0 " +
            "ORDER BY embed_pergunta <=> ?1 LIMIT ?2"
        );
        query.setParameter(1, embeddingStr);
        query.setParameter(2, limite);
        return query.getResultList();
    }

    public void atualizarEmbedding(int id, String embeddingStr) {
        Query query = getEntityManager().createNativeQuery(
            "UPDATE rag_aprendizado SET embed_pergunta = ?1::vector WHERE idaprendizado = ?2"
        );
        query.setParameter(1, embeddingStr);
        query.setParameter(2, id);
        query.executeUpdate();
    }

    public void registrarFeedback(int id, int feedback) {
        Query query = getEntityManager().createNativeQuery(
            "UPDATE rag_aprendizado SET feedback = ?1 WHERE idaprendizado = ?2"
        );
        query.setParameter(1, feedback);
        query.setParameter(2, id);
        query.executeUpdate();
    }

    public void incrementarUso(int id) {
        Query query = getEntityManager().createNativeQuery(
            "UPDATE rag_aprendizado SET usos = usos + 1 WHERE idaprendizado = ?1"
        );
        query.setParameter(1, id);
        query.executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<RagAprendizado> listarComFeedback() {
        return getEntityManager().createNativeQuery(
            "SELECT * FROM rag_aprendizado ORDER BY data_criacao DESC LIMIT 100", RagAprendizado.class
        ).getResultList();
    }
}
