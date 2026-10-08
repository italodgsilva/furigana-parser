package com.shinji2324.furigana.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link JsonWriter}.
 */
class JsonWriterTest {

    private final JsonWriter writer = new JsonWriter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Serializa entradas com id, timestamps, texto e segmentos")
    void serializesEntriesWithMetadata() throws Exception {
        FuriganaDocument document = new FuriganaDocument(List.of(
                new FuriganaEntry("1", "00:00:01,000", "00:00:02,000", "今日は",
                        List.of(new FuriganaSegment("今日", "きょう"), new FuriganaSegment("は", null)))));

        JsonNode root = mapper.readTree(writer.write(document));

        assertTrue(root.has("entries"));
        JsonNode entry = root.get("entries").get(0);
        assertEquals("1", entry.get("id").asText());
        assertEquals("00:00:01,000", entry.get("start").asText());
        assertEquals("00:00:02,000", entry.get("end").asText());
        assertEquals("今日は", entry.get("text").asText());

        JsonNode segments = entry.get("segments");
        assertEquals(2, segments.size());
        assertEquals("今日", segments.get(0).get("text").asText());
        assertEquals("きょう", segments.get(0).get("reading").asText());
        assertEquals("は", segments.get(1).get("text").asText());
        assertTrue(segments.get(1).get("reading").isNull(), "leitura ausente deve ser null no JSON");
    }

    @Test
    @DisplayName("Serve como saída verificável de um pipeline completo")
    void roundTripFromPipelineExample() throws Exception {
        FuriganaDocument document = new FuriganaDocument(List.of(
                new FuriganaEntry("1", "00:00:01,000", "00:00:04,500", "今日は日本語を勉強します。",
                        List.of(new FuriganaSegment("今日", "きょう"),
                                new FuriganaSegment("は", null),
                                new FuriganaSegment("日本語", "にほんご")))));

        String json = writer.write(document);

        JsonNode root = mapper.readTree(json);
        assertEquals(1, root.get("entries").size());
        assertEquals("にほんご", root.get("entries").get(0).get("segments").get(2).get("reading").asText());
    }

    @Test
    @DisplayName("Serializa documento vazio como lista de entradas vazia")
    void serializesEmptyDocument() throws Exception {
        JsonNode root = mapper.readTree(writer.write(new FuriganaDocument()));

        assertTrue(root.has("entries"));
        assertEquals(0, root.get("entries").size());
    }

    @Test
    @DisplayName("Retorna conteúdo vazio para documento nulo")
    void returnsEmptyForNullDocument() throws Exception {
        assertEquals("", writer.write(null));
    }

    @Test
    @DisplayName("Preserva null id de entries sem numeração")
    void preservesNullId() throws Exception {
        FuriganaDocument document = new FuriganaDocument(List.of(
                new FuriganaEntry(null, "00:00:01,000", "00:00:02,000", "texto", List.of())));

        JsonNode entry = mapper.readTree(writer.write(document)).get("entries").get(0);

        assertTrue(entry.get("id").isNull(), "id nulo deve ser preservado no JSON");
    }
}