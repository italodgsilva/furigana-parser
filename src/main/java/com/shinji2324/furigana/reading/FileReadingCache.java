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
 * Cache em disco das leituras obtidas de fontes externas.
 *
 * <p>O arquivo é um objeto JSON que mapeia a superfície do termo à sua leitura:
 *
 * <pre>
 * {
 *   "日本語" : "にほんご",
 *   "勉強" : "べんきょう"
 * }
 * </pre>
 *
 * <p>Ao ser carregada do arquivo, uma leitura é marcada com a fonte {@code cache}
 * (a origem original — Jisho, por exemplo — não é preservada). O cache é
 * <em>best-effort</em>: falhas de leitura ou escrita são ignoradas para não
 * interromper o processamento da legenda.
 *
 * <p>O mapa é ordenado por chave, produzindo um arquivo estável entre execuções.
 */
public class FileReadingCache {

    private static final TypeReference<Map<String, String>> CACHE_TYPE = new TypeReference<>() {
    };

    private final Path cacheFile;
    private final ObjectMapper objectMapper;
    private final ObjectWriter writer;
    private final Map<String, Reading> entries;

    /**
     * Carrega o cache do arquivo informado (ou começa vazio, se ele não existir).
     *
     * @param cacheFile caminho do arquivo de cache, ou {@code null} para um cache apenas em memória
     */
    public FileReadingCache(Path cacheFile) {
        this(cacheFile, new ObjectMapper());
    }

    /**
     * Carrega o cache usando um {@link ObjectMapper} informado.
     *
     * @param cacheFile    caminho do arquivo, ou {@code null}
     * @param objectMapper mapper usado para ler/gravar o JSON
     */
    public FileReadingCache(Path cacheFile, ObjectMapper objectMapper) {
        this.cacheFile = cacheFile;
        this.objectMapper = objectMapper;
        this.writer = objectMapper.writerWithDefaultPrettyPrinter();
        this.entries = new TreeMap<>();
        load();
    }

    /**
     * Retorna a leitura armazenada para uma chave.
     *
     * @param key superfície do termo
     * @return a leitura, ou {@code null} se ausente
     */
    public Reading get(String key) {
        return entries.get(key);
    }

    /**
     * Armazena uma leitura e persiste o cache.
     *
     * @param key     superfície do termo
     * @param reading leitura a armazenar
     */
    public void put(String key, Reading reading) {
        entries.put(key, reading);
        save();
    }

    /**
     * Informa se a chave está presente no cache.
     *
     * @param key superfície do termo
     * @return {@code true} se houver leitura armazenada
     */
    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    /**
     * Retorna a quantidade de leituras armazenadas.
     *
     * @return número de entradas
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
            // Cache é best-effort: um arquivo corrompido não deve impedir o processamento.
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
            // Cache é best-effort: falha de escrita não deve interromper o processamento.
        }
    }
}
