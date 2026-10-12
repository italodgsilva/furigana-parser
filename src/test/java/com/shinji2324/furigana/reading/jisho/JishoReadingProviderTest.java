package com.shinji2324.furigana.reading.jisho;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JishoReadingProvider}.
 *
 * <p>The HTTP layer is replaced by an {@link HttpTextFetcher} that returns
 * pre-recorded responses, keeping the tests deterministic and offline.
 */
class JishoReadingProviderTest {

    private static final String VALID_RESPONSE = """
            {
              "meta": { "status": 200 },
              "data": [
                {
                  "japanese": [
                    { "word": "日本語", "reading": "にほんご" }
                  ],
                  "senses": [ { "english_definitions": [ "Japanese language" ] } ]
                }
              ]
            }
            """;

    @Test
    @DisplayName("Extracts the reading from the Japanese form that matches the surface")
    void extractsReadingForMatchingSurface() {
        List<String> requestedUrls = new ArrayList<>();
        JishoReadingProvider provider = new JishoReadingProvider(url -> {
            requestedUrls.add(url);
            return Optional.of(VALID_RESPONSE);
        });

        Optional<Reading> reading = provider.findReading(new Token("日本語", "名詞", "ニホンゴ"));

        assertTrue(reading.isPresent());
        assertEquals("にほんご", reading.get().getReading());
        assertEquals("jisho", reading.get().getSource());
        assertEquals("日本語", reading.get().getSurface());

        String url = requestedUrls.get(0);
        assertTrue(url.startsWith(JishoReadingProvider.DEFAULT_ENDPOINT), url);
        assertTrue(url.contains("?keyword=" + URLEncoder.encode("日本語", StandardCharsets.UTF_8)), url);
    }

    @Test
    @DisplayName("Uses the configured endpoint")
    void usesConfiguredEndpoint() {
        List<String> requestedUrls = new ArrayList<>();
        JishoReadingProvider provider = new JishoReadingProvider(
                "https://example.test/words",
                url -> {
                    requestedUrls.add(url);
                    return Optional.of(VALID_RESPONSE);
                },
                new ObjectMapper());

        provider.findReading(new Token("日本語", "名詞"));

        assertTrue(requestedUrls.get(0).startsWith("https://example.test/words?keyword="), requestedUrls.get(0));
    }

    @Test
    @DisplayName("Returns empty when no form matches exactly")
    void returnsEmptyWhenNoExactMatch() {
        JishoReadingProvider provider = new JishoReadingProvider(url -> Optional.of(VALID_RESPONSE));

        assertTrue(provider.findReading(new Token("勉強", "名詞")).isEmpty());
    }

    @Test
    @DisplayName("Returns empty when the request fails")
    void returnsEmptyWhenFetchFails() {
        JishoReadingProvider provider = new JishoReadingProvider(url -> Optional.empty());

        assertTrue(provider.findReading(new Token("日本語", "名詞")).isEmpty());
    }

    @Test
    @DisplayName("Returns empty for malformed JSON or JSON without a data list")
    void returnsEmptyForMalformedResponse() {
        JishoReadingProvider malformed = new JishoReadingProvider(url -> Optional.of("not json"));
        assertTrue(malformed.findReading(new Token("日本語", "名詞")).isEmpty());

        JishoReadingProvider withoutData = new JishoReadingProvider(url -> Optional.of("{\"meta\":{}}"));
        assertTrue(withoutData.findReading(new Token("日本語", "名詞")).isEmpty());
    }

    @Test
    @DisplayName("Does not query the endpoint for a null or blank token")
    void skipsInvalidTokens() {
        AtomicInteger calls = new AtomicInteger();
        JishoReadingProvider provider = new JishoReadingProvider(url -> {
            calls.incrementAndGet();
            return Optional.of(VALID_RESPONSE);
        });

        assertTrue(provider.findReading(null).isEmpty());
        assertTrue(provider.findReading(new Token("   ", "WHITESPACE")).isEmpty());
        assertEquals(0, calls.get());
    }
}
