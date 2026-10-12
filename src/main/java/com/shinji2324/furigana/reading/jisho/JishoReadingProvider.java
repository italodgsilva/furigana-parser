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
 * Reading provider based on the public Jisho API.
 *
 * <p>Queries {@code https://jisho.org/api/v1/search/words?keyword=<term>} and
 * uses the first Japanese form whose {@code word} matches the token surface
 * exactly. Jisho returns the reading in hiragana.
 *
 * <p>Only exact matches are accepted: for conjugated forms Jisho usually returns
 * the dictionary form (e.g. {@code 食べる} for {@code 食べた}), and using that
 * reading directly could produce incorrect furigana. In that case the provider
 * returns empty and the tokenizer's reading (Kuromoji) prevails.
 *
 * <p>Network failures and malformed responses result in
 * {@link Optional#empty()}; the pipeline keeps working without furigana for the
 * token.
 */
public class JishoReadingProvider implements ReadingProvider {

    /** Default endpoint of the Jisho search API. */
    public static final String DEFAULT_ENDPOINT = "https://jisho.org/api/v1/search/words";

    private final String endpoint;
    private final HttpTextFetcher fetcher;
    private final ObjectMapper objectMapper;

    /** Creates the provider with the default endpoint and HTTP fetcher. */
    public JishoReadingProvider() {
        this(DEFAULT_ENDPOINT, HttpTextFetcher.defaultFetcher(), new ObjectMapper());
    }

    /**
     * Creates the provider with the default endpoint and a given fetcher (useful in tests).
     *
     * @param fetcher HTTP request layer
     */
    public JishoReadingProvider(HttpTextFetcher fetcher) {
        this(DEFAULT_ENDPOINT, fetcher, new ObjectMapper());
    }

    /**
     * Creates the complete provider.
     *
     * @param endpoint      base URL of the API
     * @param fetcher       HTTP request layer
     * @param objectMapper  mapper used to interpret the JSON response
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
     * Extracts the reading of the surface from the Jisho JSON.
     *
     * @param surface queried term
     * @param json    response body
     * @return reading found, or empty
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
