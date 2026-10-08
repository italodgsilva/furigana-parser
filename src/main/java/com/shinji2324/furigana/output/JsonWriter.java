package com.shinji2324.furigana.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinji2324.furigana.furigana.FuriganaDocument;

/**
 * Serializa um {@link FuriganaDocument} para o formato JSON intermediário.
 *
 * <p>Utiliza o Jackson para garantir uma serialização consistente com o modelo.
 * A estrutura gerada é estável e serve como saída verificável de cada etapa do
 * pipeline e como entrada para os geradores de formato.
 *
 * <p>Exemplo de saída (leitura abreviada):
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

    /** Cria o escritor com um {@link ObjectMapper} padrão. */
    public JsonWriter() {
        this(new ObjectMapper());
    }

    /**
     * Cria o escritor com o mapper informado (permite configuração externa).
     *
     * @param objectMapper mapper a ser utilizado na serialização
     */
    public JsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializa o documento para JSON, formatado para leitura.
     *
     * @param document documento a ser serializado
     * @return representação JSON do documento
     * @throws JsonProcessingException se a serialização falhar
     */
    public String write(FuriganaDocument document) throws JsonProcessingException {
        if (document == null) {
            return "";
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
    }
}