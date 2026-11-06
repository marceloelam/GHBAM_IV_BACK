package br.com.iv.menufavorito;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Telas favoritas do menu lateral. SQL nativo de proposito: sem @Entity, a validacao de schema
 * do Hibernate em producao nao depende desta tabela para o sistema subir.
 */
@ApplicationScoped
public class MenuFavoritoService {

    static final int LIMITE = 20;
    static final int TAMANHO_MAXIMO = 300;

    @Inject
    EntityManager em;

    /** Caminho interno da aplicacao: nunca URL externa, nem texto com caracteres de controle. */
    static boolean caminhoValido(String caminho) {
        if (caminho == null || caminho.length() < 2 || caminho.length() > TAMANHO_MAXIMO) return false;
        if (caminho.charAt(0) != '/' || caminho.charAt(1) == '/' || caminho.contains("\\") || caminho.contains("://")) return false;
        return caminho.chars().noneMatch(c -> c <= ' ' || c == 127);
    }

    @SuppressWarnings("unchecked")
    public List<String> listar(String matricula) {
        return em.createNativeQuery(
                "SELECT caminho FROM usuario_menu_favorito WHERE matricula = :m ORDER BY criado_em, caminho")
                .setParameter("m", matricula)
                .getResultList();
    }

    @Transactional
    public void adicionar(String matricula, String caminho) {
        long total = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM usuario_menu_favorito WHERE matricula = :m AND caminho <> :c")
                .setParameter("m", matricula)
                .setParameter("c", caminho)
                .getSingleResult()).longValue();
        if (total >= LIMITE) {
            throw new IllegalStateException("Limite de " + LIMITE + " favoritos atingido. Remova um para adicionar outro.");
        }
        em.createNativeQuery(
                "INSERT INTO usuario_menu_favorito (matricula, caminho) VALUES (:m, :c) ON CONFLICT DO NOTHING")
                .setParameter("m", matricula)
                .setParameter("c", caminho)
                .executeUpdate();
    }

    @Transactional
    public void remover(String matricula, String caminho) {
        em.createNativeQuery("DELETE FROM usuario_menu_favorito WHERE matricula = :m AND caminho = :c")
                .setParameter("m", matricula)
                .setParameter("c", caminho)
                .executeUpdate();
    }
}
