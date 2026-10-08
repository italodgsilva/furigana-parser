package com.shinji2324.furigana.reading.jisho;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import com.shinji2324.furigana.reading.ReadingProvider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Provider de leituras baseado na API pública do Jisho.
 *
 * <p>Consulta {@code https://jisho.org/api/v1/search/words?keyword=<termo>} e
 * usa a primeira forma japonesa cujo {@code word} coincide exatamente com a
 * superfície do token. O Jisho devolve a leitura em hiragana.
 *
 * <p>Apenas coincidências exatas são aceitas: para formas conjugadas o Jisho
 * costuma retornar o dicionário (ex.: {@code 食べる} para {@code 食べた}), e usar
 * essa leitura diretamente poderia produzir furigana incorreto. Nesse caso o
 * provider devolve vazio e a leitura do tokenizador (Kuromoji) prevalece.
 *
 * <p>Falhas de rede e respostas malformadas resultam em {@link Optional#empty()};
 * o pipeline continua funcionando sem furigana para o token.
 */
public class JishoReadingProvider implements ReadingProvider {

    /** Endpoint padrão da API de busca do Jisho. */
    public static final String DEFAULT_ENDPOINT = "https://jisho.org/api/v1/search/words";

    private final String endpoint;
    private final HttpTextFetcher fetcher;
    private final ObjectMapper objectMapper;

    /** Cria o provider com o endpoint e o fetcher HTTP padrão. */
    public JishoReadingProvider() {
        this(DEFAULT_ENDPOINT, HttpTextFetcher.defaultFetcher(), new ObjectMapper());
    }

    /**
     * Cria o provider com o endpoint padrão e um fetcher informado (útil em testes).
     *
     * @param fetcher camada de requisição HTTP
     */
    public JishoReadingProvider(HttpTextFetcher fetcher) {
        this(DEFAULT_ENDPOINT, fetcher, new ObjectMapper());
    }

    /**
     * Cria o provider completo.
     *
     * @param endpoint      URL base da API
     * @param fetcher       camada de requisição HTTP
     * @param objectMapper  mapper usado para interpretar a resposta JSON
     */
    public JishoReadingProvider(String endpoint, HttpTextFetcher fetcher, ObjectMapper objectMapper) {
        this.endpoint = endpoint;
        this.fetcher = fetcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Reading> findReading(Token token) {
        if (token == null || token.getSurface() == null || token.getSurface().isBlank()) {
            return Optional.empty();
        }

        String surface = token.getSurface();
        String url = endpoint + "?keyword=" + URLEncoder.encode(surface, StandardCharsets.UTF_8);

        return fetcher.fetch(url).flatMap(body -> extractReading(surface, body));
    }

    /**
     * Extrai a leitura da superfície a partir do JSON do Jisho.
     *
     * @param surface termo consultado
     * @param json    corpo da resposta
     * @return leitura encontrada, ou vazio
     */
    private Optional<Reading> extractReading(String surface, String json) {
        try {
            JsonNode data = objectMapper.readTree(json).path("data");
            if (!data.isArray()) {
                return Optional.empty();
            }

            for (JsonNode entry : data) {
                JsonNode japanese = entry.path("japanese");
                if (!japanese.isArray()) {
                    continue;
                }
                for (JsonNode form : japanese) {
                    String word = form.path("word").asText(null);
                    String reading = form.path("reading").asText(null);
                    if (surface.equals(word) && reading != null && !reading.isBlank()) {
                        return Optional.of(new Reading(surface, reading, "jisho"));
                    }
                }
            }
            return Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
