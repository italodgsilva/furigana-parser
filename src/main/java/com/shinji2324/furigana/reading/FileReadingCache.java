package com.shinji2324.furigana.reading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * On-disk cache of the readings obtained from external sources.
 *
 * <p>The file is a JSON object that maps the surface of the term to its reading:
 *
 * <pre>
 * {
 *   "日本語" : "にほんご",
 *   "勉強" : "べんきょう"
 * }
 * </pre>
 *
 * <p>When loaded from the file, a reading is marked with the source {@code cache}
 * (the original origin — Jisho, for example — is not preserved). The cache is
 * <em>best-effort</em>: read or write failures are ignored so as not to interrupt
 * subtitle processing.
 *
 * <p>The map is ordered by key, producing a stable file across runs.
 */
public class FileReadingCache {

    private static final TypeReference<Map<String, String>> CACHE_TYPE = new TypeReference<>() {
    };

    private final Path cacheFile;
    private final ObjectMapper objectMapper;
    private final ObjectWriter writer;
    private final Map<String, Reading> entries;

    /**
     * Loads the cache from the given file (or starts empty, if it does not exist).
     *
     * @param cacheFile path to the cache file, or {@code null} for an in-memory-only cache
     */
    public FileReadingCache(Path cacheFile) {
        this(cacheFile, new ObjectMapper());
    }

    /**
     * Loads the cache using a given {@link ObjectMapper}.
     *
     * @param cacheFile    path to the file, or {@code null}
     * @param objectMapper mapper used to read/write the JSON
     */
    public FileReadingCache(Path cacheFile, ObjectMapper objectMapper) {
        this.cacheFile = cacheFile;
        this.objectMapper = objectMapper;
        this.writer = objectMapper.writerWithDefaultPrettyPrinter();
        this.entries = new TreeMap<>();
        load();
    }

    /**
     * Returns the reading stored for a key.
     *
     * @param key surface of the term
     * @return the reading, or {@code null} if absent
     */
    public Reading get(String key) {
        return entries.get(key);
    }

    /**
     * Stores a reading and persists the cache.
     *
     * @param key     surface of the term
     * @param reading reading to store
     */
    public void put(String key, Reading reading) {
        entries.put(key, reading);
        save();
    }

    /**
     * Tells whether the key is present in the cache.
     *
     * @param key surface of the term
     * @return {@code true} if there is a stored reading
     */
    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    /**
     * Returns the number of stored readings.
     *
     * @return number of entries
     */
    public int size() {
        return entries.size();
    }

    private void load() {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return;
        }

        try {
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return;
            }
            Map<String, String> stored = objectMapper.readValue(json, CACHE_TYPE);
            stored.forEach((key, reading) -> {
                if (reading != null && !reading.isBlank()) {
                    entries.put(key, new Reading(key, reading, "cache"));
                }
            });
        } catch (IOException ignored) {
            // Cache is best-effort: a corrupted file must not prevent processing.
        }
    }

    private void save() {
        if (cacheFile == null) {
            return;
        }

        try {
            if (cacheFile.getParent() != null) {
                Files.createDirectories(cacheFile.getParent());
            }
            Map<String, String> stored = new TreeMap<>();
            entries.forEach((key, reading) -> stored.put(key, reading.getReading()));
            Files.writeString(cacheFile, writer.writeValueAsString(stored), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Cache is best-effort: a write failure must not interrupt processing.
        }
    }
}
