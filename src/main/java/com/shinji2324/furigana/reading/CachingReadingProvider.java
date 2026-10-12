package com.shinji2324.furigana.reading;

import com.shinji2324.furigana.japanese.Token;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Decorator of {@link ReadingProvider} that avoids repeated lookups.
 *
 * <p>Flow for each token:
 * <ol>
 *   <li>if the surface is already in the {@link FileReadingCache}, returns the cached reading
 *       without consulting the delegate provider;</li>
 *   <li>otherwise, consults the delegate and, on success, stores the result in the cache;</li>
 *   <li>terms not found are recorded only in memory during the current run — failures
 *       (including service unavailability) are not persisted, so that they can be retried
 *       in future runs.</li>
 * </ol>
 *
 * <p>The cache is looked up by the token surface, which is the stable key across
 * runs and independent of the position in the subtitle.
 */
public class CachingReadingProvider implements ReadingProvider {

    private final ReadingProvider delegate;
    private final FileReadingCache cache;
    private final Set<String> misses = new HashSet<>();

    /**
     * Creates the decorator.
     *
     * @param delegate provider consulted in case of a cache miss
     * @param cache    on-disk/in-memory cache
     */
    public CachingReadingProvider(ReadingProvider delegate, FileReadingCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Optional<Reading> findReading(Token token) {
        if (token == null || token.getSurface() == null || token.getSurface().isBlank()) {
            return Optional.empty();
        }

        String key = token.getSurface();

        Reading cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        if (misses.contains(key)) {
            return Optional.empty();
        }

        Optional<Reading> found = delegate.findReading(token);
        found.ifPresent(reading -> cache.put(key, reading));
        if (found.isEmpty()) {
            misses.add(key);
        }
        return found;
    }
}
