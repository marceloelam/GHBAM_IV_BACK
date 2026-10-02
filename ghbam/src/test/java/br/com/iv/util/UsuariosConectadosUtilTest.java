package br.com.iv.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UsuariosConectadosUtilTest {

    @BeforeEach
    public void setup() {
        // limpar estado interno via reflexão não desejado; usar nomes únicos por teste
    }

    @Test
    public void testAdicionarERemoverPorMatricula() {
        String unidade = "unidadeTest";
        // adicionar dois usuarios com matrículas diferentes
    UsuariosConectadosUtil.adicionarUsuario(unidade, "Usuario A", "0000", "cargo", "espA", "fotoA");
    UsuariosConectadosUtil.adicionarUsuario(unidade, "Usuario B", "1111", "cargo", "espB", "fotoB");

        Set<UsuarioConectado> antes = UsuariosConectadosUtil.listarUsuarios(unidade);
        assertEquals(2, antes.size(), "Devem existir 2 usuários após adição");

        boolean removed = UsuariosConectadosUtil.removerUsuarioPorMatricula(unidade, "0000");
        assertTrue(removed, "Remoção da matrícula 0000 deve retornar true");

        Set<UsuarioConectado> depois = UsuariosConectadosUtil.listarUsuarios(unidade);
        assertEquals(1, depois.size(), "Deve sobrar apenas 1 usuário após remover 0000");
        UsuarioConectado restante = depois.iterator().next();
        assertEquals("1111", restante.getMatricula(), "O usuário restante deve ser com matrícula 1111");
    }

    @Test
    public void testRemoverMatriculaInexistente() {
        String unidade = "unidadeTest2";
    UsuariosConectadosUtil.adicionarUsuario(unidade, "Usuario X", "2222", "cargo", "espX", "fotoX");

        boolean removed = UsuariosConectadosUtil.removerUsuarioPorMatricula(unidade, "9999");
        assertFalse(removed, "Remoção de matrícula inexistente deve retornar false");

        Set<UsuarioConectado> depois = UsuariosConectadosUtil.listarUsuarios(unidade);
        assertEquals(1, depois.size(), "Nenhum usuário deve ter sido removido");
    }

    @Test
    public void testRemoveInactiveUsers() throws InterruptedException {
        String unidade = "unidadeTest3";
        
        // Adiciona usuário ativo
        UsuariosConectadosUtil.adicionarUsuario(unidade, "Usuario Ativo", "3333", "cargo", "esp", "foto");
        
        // Adiciona usuário que será inativo
        UsuariosConectadosUtil.adicionarUsuario(unidade, "Usuario Inativo", "4444", "cargo", "esp", "foto");
        
        // Verifica que ambos foram adicionados
        Set<UsuarioConectado> usuarios = UsuariosConectadosUtil.listarUsuarios(unidade);
        assertEquals(2, usuarios.size(), "Devem existir 2 usuários");
        
        // Aguarda 2 segundos para simular inatividade
        Thread.sleep(2000);
        
        // Atualiza apenas o usuário ativo
        UsuariosConectadosUtil.adicionarUsuario(unidade, "Usuario Ativo", "3333", "cargo", "esp", "foto");
        
        // Define cutoff para 1 segundo atrás
        long cutoff = System.currentTimeMillis() - 1000;
        
        // Remove usuários inativos (pode remover de outras unidades também)
        int removed = UsuariosConectadosUtil.removeInactiveUsers(cutoff);
        
        // Verifica que pelo menos 1 usuário foi removido (pode ser mais se outros testes deixaram usuários)
        assertTrue(removed >= 1, "Deve ter removido pelo menos 1 usuário inativo");
        
        // Verifica que apenas o usuário ativo permanece NA UNIDADE ESPECÍFICA
        Set<UsuarioConectado> aposLimpeza = UsuariosConectadosUtil.listarUsuarios(unidade);
        assertEquals(1, aposLimpeza.size(), "Deve sobrar apenas 1 usuário na unidade de teste");
        
        UsuarioConectado restante = aposLimpeza.iterator().next();
        assertEquals("3333", restante.getMatricula(), "O usuário ativo deve permanecer");
    }
}
