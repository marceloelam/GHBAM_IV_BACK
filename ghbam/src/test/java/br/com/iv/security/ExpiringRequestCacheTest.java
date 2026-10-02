package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class ExpiringRequestCacheTest {

    @Test
    void deveConsolidarCarregamentosDuranteOTtlERecarregarDepois() {
        AtomicLong clock = new AtomicLong(1_000);
        AtomicInteger loads = new AtomicInteger();
        ExpiringRequestCache<String, String> cache =
                new ExpiringRequestCache<>(500, 10, clock::get);

        assertEquals("usuario-1", cache.get("33749", key -> "usuario-" + loads.incrementAndGet()));
        assertEquals("usuario-1", cache.get("33749", key -> "usuario-" + loads.incrementAndGet()));
        assertEquals(1, loads.get());

        clock.addAndGet(501);

        assertEquals("usuario-2", cache.get("33749", key -> "usuario-" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    void deveRecarregarImediatamenteDepoisDaInvalidacao() {
        AtomicLong clock = new AtomicLong(1_000);
        AtomicInteger loads = new AtomicInteger();
        ExpiringRequestCache<String, Integer> cache =
                new ExpiringRequestCache<>(5_000, 10, clock::get);

        assertEquals(1, cache.get("33749", key -> loads.incrementAndGet()));
        cache.invalidate("33749");
        assertEquals(2, cache.get("33749", key -> loads.incrementAndGet()));
    }
}
