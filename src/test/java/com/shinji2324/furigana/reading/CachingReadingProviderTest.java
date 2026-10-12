package com.shinji2324.furigana.reading;

import com.shinji2324.furigana.japanese.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CachingReadingProvider}.
 */
class CachingReadingProviderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Queries the delegate once and reuses the cached reading")
    void cachesSuccessfulLookups() {
        AtomicInteger calls = new AtomicInteger();
        ReadingProvider delegate = token -> {
            calls.incrementAndGet();
            return Optional.of(new Reading(token.getSurface(), "にほんご", "jisho"));
        };
        FileReadingCache cache = new FileReadingCache(tempDir.resolve("readings.json"));
        CachingReadingProvider provider = new CachingReadingProvider(delegate, cache);

        Token token = new Token("日本語", "名詞", "ニホンゴ");
        assertEquals("にほんご", provider.findReading(token).orElseThrow().getReading());
        assertEquals("にほんご", provider.findReading(token).orElseThrow().getReading());

        assertEquals(1, calls.get());
        assertTrue(cache.contains("日本語"));
    }

    @Test
    @DisplayName("Uses an already persisted reading without querying the delegate")
    void usesPersistedCache() {
        Path file = tempDir.resolve("readings.json");
        new FileReadingCache(file).put("日本語", new Reading("日本語", "にほんご", "jisho"));

        AtomicInteger calls = new AtomicInteger();
        ReadingProvider delegate = token -> {
            calls.incrementAndGet();
            return Optional.empty();
        };
        CachingReadingProvider provider = new CachingReadingProvider(delegate, new FileReadingCache(file));

        assertEquals("にほんご", provider.findReading(new Token("日本語", "名詞")).orElseThrow().getReading());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("Does not repeat failed lookups within the same run (without persisting them)")
    void doesNotRepeatMissesWithinRunAndDoesNotPersistThem() {
        Path file = tempDir.resolve("readings.json");
        AtomicInteger calls = new AtomicInteger();
        ReadingProvider delegate = token -> {
            calls.incrementAndGet();
            return Optional.empty();
        };
        CachingReadingProvider provider = new CachingReadingProvider(delegate, new FileReadingCache(file));

        Token token = new Token("難読", "名詞");
        assertTrue(provider.findReading(token).isEmpty());
        assertTrue(provider.findReading(token).isEmpty());

        assertEquals(1, calls.get(), "the miss should be remembered in memory only");
        assertFalse(new FileReadingCache(file).contains("難読"), "misses must not be persisted");
    }

    @Test
    @DisplayName("Does not query the delegate for a null or blank token")
    void skipsInvalidTokens() {
        AtomicInteger calls = new AtomicInteger();
        ReadingProvider delegate = token -> {
            calls.incrementAndGet();
            return Optional.empty();
        };
        CachingReadingProvider provider = new CachingReadingProvider(delegate, new FileReadingCache(null));

        assertTrue(provider.findReading(null).isEmpty());
        assertTrue(provider.findReading(new Token("  ", "WHITESPACE")).isEmpty());
        assertEquals(0, calls.get());
    }
}
