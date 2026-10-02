package br.com.iv.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Cache local de curtissima duracao para consolidar rajadas de requisicoes.
 * Nao substitui o banco como fonte de verdade e nunca armazena entidades JPA.
 */
public final class ExpiringRequestCache<K, V> {
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxEntries;
    private final LongSupplier clock;

    public ExpiringRequestCache(long ttlMillis, int maxEntries) {
        this(ttlMillis, maxEntries, System::currentTimeMillis);
    }

    ExpiringRequestCache(long ttlMillis, int maxEntries, LongSupplier clock) {
        this.ttlMillis = Math.max(1, ttlMillis);
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    public V get(K key, Function<K, V> loader) {
        if (key == null) {
            return loader.apply(null);
        }

        long now = clock.getAsLong();
        Entry<V> current = entries.get(key);
        if (current != null && current.expiresAt() > now) {
            return current.value();
        }

        if (entries.size() >= maxEntries) {
            entries.entrySet().removeIf(item -> item.getValue().expiresAt() <= now);
            if (entries.size() >= maxEntries) {
                entries.clear();
            }
        }

        return entries.compute(key, (cacheKey, existing) -> {
            long computeNow = clock.getAsLong();
            if (existing != null && existing.expiresAt() > computeNow) {
                return existing;
            }
            return new Entry<>(loader.apply(cacheKey), computeNow + ttlMillis);
        }).value();
    }

    public void invalidate(K key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    public void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private record Entry<V>(V value, long expiresAt) {
    }
}
