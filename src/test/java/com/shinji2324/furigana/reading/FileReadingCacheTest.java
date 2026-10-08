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
 * Testes do {@link FileReadingCache}.
 */
class FileReadingCacheTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Guarda e recupera uma leitura em memória")
    void putAndGetRoundTrip() {
        Path file = tempDir.resolve("cache.txt");
        FileReadingCache cache = new FileReadingCache(file);

        cache.put("日本語", new Reading("日本語", "にほんご", "jisho"));

        assertTrue(cache.contains("日本語"));
        assertEquals("にほんご", cache.get("日本語").getReading());
        assertEquals("jisho", cache.get("日本語").getSource());
    }

    @Test
    @DisplayName("Persiste as entradas e as recarrega em uma nova instância")
    void persistedEntriesAreLoadedOnNextInstantiation() {
        Path file = tempDir.resolve("cache.txt");
        new FileReadingCache(file).put("日本語", new Reading("日本語", "にほんご", "jisho"));

        FileReadingCache reloaded = new FileReadingCache(file);

        assertTrue(reloaded.contains("日本語"));
        assertEquals("にほんご", reloaded.get("日本語").getReading());
        // Leituras carregadas do arquivo são marcadas como vindas do cache.
        assertEquals("cache", reloaded.get("日本語").getSource());
    }

    @Test
    @DisplayName("Não falha quando o arquivo de cache não existe")
    void constructorHandlesMissingFile() {
        FileReadingCache cache = new FileReadingCache(tempDir.resolve("nao-existe.txt"));

        assertFalse(cache.contains("qualquer"));
    }

    @Test
    @DisplayName("Retorna nulo para chave ausente e aceita caminho nulo")
    void getReturnsNullForMissingKeyAndAcceptsNullPath() {
        FileReadingCache cache = new FileReadingCache(null);

        assertNull(cache.get("ausente"));
        // Gravar com caminho nulo apenas atualiza a memória, sem falhar.
        cache.put("presente", new Reading("presente", "リーディング"));
        assertEquals("リーディング", cache.get("presente").getReading());
    }

    @Test
    @DisplayName("Persiste o cache como um objeto JSON de superfície para leitura")
    void persistsAsJsonObject() throws Exception {
        Path file = tempDir.resolve("readings.json");
        new FileReadingCache(file).put("日本語", new Reading("日本語", "にほんご", "jisho"));

        JsonNode root = new ObjectMapper().readTree(Files.readString(file, StandardCharsets.UTF_8));

        assertTrue(root.isObject());
        assertEquals("にほんご", root.get("日本語").asText());
    }
}