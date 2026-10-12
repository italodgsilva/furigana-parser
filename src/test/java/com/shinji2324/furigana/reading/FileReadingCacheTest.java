package com.shinji2324.furigana.reading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FileReadingCache}.
 */
class FileReadingCacheTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Stores and retrieves a reading in memory")
    void putAndGetRoundTrip() {
        Path file = tempDir.resolve("cache.txt");
        FileReadingCache cache = new FileReadingCache(file);

        cache.put("日本語", new Reading("日本語", "にほんご", "jisho"));

        assertTrue(cache.contains("日本語"));
        assertEquals("にほんご", cache.get("日本語").getReading());
        assertEquals("jisho", cache.get("日本語").getSource());
    }

    @Test
    @DisplayName("Persists the entries and reloads them in a new instance")
    void persistedEntriesAreLoadedOnNextInstantiation() {
        Path file = tempDir.resolve("cache.txt");
        new FileReadingCache(file).put("日本語", new Reading("日本語", "にほんご", "jisho"));

        FileReadingCache reloaded = new FileReadingCache(file);

        assertTrue(reloaded.contains("日本語"));
        assertEquals("にほんご", reloaded.get("日本語").getReading());
        // Readings loaded from the file are marked as coming from the cache.
        assertEquals("cache", reloaded.get("日本語").getSource());
    }

    @Test
    @DisplayName("Does not fail when the cache file does not exist")
    void constructorHandlesMissingFile() {
        FileReadingCache cache = new FileReadingCache(tempDir.resolve("does-not-exist.txt"));

        assertFalse(cache.contains("any-key"));
    }

    @Test
    @DisplayName("Returns null for a missing key and accepts a null path")
    void getReturnsNullForMissingKeyAndAcceptsNullPath() {
        FileReadingCache cache = new FileReadingCache(null);

        assertNull(cache.get("missing"));
        // Writing with a null path only updates memory, without failing.
        cache.put("present", new Reading("present", "リーディング"));
        assertEquals("リーディング", cache.get("present").getReading());
    }

    @Test
    @DisplayName("Persists the cache as a JSON object of surface to reading")
    void persistsAsJsonObject() throws Exception {
        Path file = tempDir.resolve("readings.json");
        new FileReadingCache(file).put("日本語", new Reading("日本語", "にほんご", "jisho"));

        JsonNode root = new ObjectMapper().readTree(Files.readString(file, StandardCharsets.UTF_8));

        assertTrue(root.isObject());
        assertEquals("にほんご", root.get("日本語").asText());
    }
}