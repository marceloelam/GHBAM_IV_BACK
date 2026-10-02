package br.com.iv.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class BamRepositoryTransferenciaTest {

    @Test
    void transfereSomentePedidosPendentesParaUnidadeDestino() throws Exception {
        BamRepository repository = new BamRepository();
        List<String> sqls = new ArrayList<>();
        List<String> parametros = new ArrayList<>();
        Query query = (Query) java.lang.reflect.Proxy.newProxyInstance(
                Query.class.getClassLoader(), new Class<?>[] { Query.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("setParameter")) {
                        parametros.add(args[0] + "=" + args[1]);
                        return proxy;
                    }
                    return method.getName().equals("executeUpdate") ? 1 : null;
                });
        EntityManager entityManager = (EntityManager) java.lang.reflect.Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[] { EntityManager.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("createNativeQuery")) {
                        sqls.add((String) args[0]);
                        return query;
                    }
                    return null;
                });

        Field field = BamRepository.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(repository, entityManager);

        Method method = BamRepository.class.getDeclaredMethod("atualizarOrigemDestinoTransferencia", int.class,
                String.class, String.class);
        method.invoke(repository, 123, "ORIGEM", "DESTINO");

        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("UPDATE pedidomedico2")
                && sql.contains("efetuado = false")));
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("UPDATE bam")
                && sql.contains("transferido = true")));
        assertTrue(parametros.contains("idbam=123"));
        assertTrue(parametros.contains("destino=DESTINO"));
    }
}
