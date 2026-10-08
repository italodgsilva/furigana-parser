package com.shinji2324.furigana.reading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class FileReadingCache {
    private final Path cacheFile;
    private final Map<String, Reading> entries;

    public FileReadingCache(Path cacheFile) {
        this.cacheFile = cacheFile;
        this.entries = new HashMap<>();
        load();
    }

    public Reading get(String key) {
        return entries.get(key);
    }

    public void put(String key, Reading reading) {
        entries.put(key, reading);
        save();
    }

    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    private void load() {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return;
        }

        try {
            Files.readAllLines(cacheFile).forEach(line -> {
                if (line == null || line.isBlank()) {
                    return;
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    entries.put(parts[0], new Reading(parts[0], parts[1], "cache"));
                }
            });
        } catch (IOException ignored) {
            // Ignored intentionally for now; file cache is a skeleton implementation.
        }
    }

    private void save() {
        if (cacheFile == null) {
            return;
        }

        try {
            Files.createDirectories(cacheFile.getParent());
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, Reading> entry : entries.entrySet()) {
                builder.append(entry.getKey())
                        .append('=')
                        .append(entry.getValue().getReading())
                        .append(System.lineSeparator());
            }
            Files.writeString(cacheFile, builder.toString());
        } catch (IOException ignored) {
            // Ignored intentionally for now; file cache is a skeleton implementation.
        }
    }
}
