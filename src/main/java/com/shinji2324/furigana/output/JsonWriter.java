package com.shinji2324.furigana.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinji2324.furigana.furigana.FuriganaDocument;

/**
 * Serializes a {@link FuriganaDocument} to the intermediate JSON format.
 *
 * <p>Uses Jackson to ensure serialization consistent with the model. The generated
 * structure is stable and serves as verifiable output of each stage of the
 * pipeline and as input for the format generators.
 *
 * <p>Example output (reading abbreviated):
 * <pre>
 * {
 *   "entries": [
 *     {
 *       "id": "1",
 *       "start": "00:00:01,000",
 *       "end": "00:00:04,500",
 *       "text": "今日は日本語を勉強します。",
 *       "segments": [
 *         { "text": "今日", "reading": "きょう" },
 *         { "text": "は", "reading": null }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
public class JsonWriter {

    private final ObjectMapper objectMapper;

    /** Creates the writer with a default {@link ObjectMapper}. */
    public JsonWriter() {
        this(new ObjectMapper());
    }

    /**
     * Creates the writer with the given mapper (allows external configuration).
     *
     * @param objectMapper mapper to be used in serialization
     */
    public JsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the document to JSON, formatted for reading.
     *
     * @param document document to be serialized
     * @return JSON representation of the document
     * @throws JsonProcessingException if serialization fails
     */
    public String write(FuriganaDocument document) throws JsonProcessingException {
        if (document == null) {
            return "";
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
    }
}