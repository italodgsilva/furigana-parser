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
 * Testes do {@link JishoReadingProvider}.
 *
 * <p>A camada HTTP é substituída por um {@link HttpTextFetcher} que devolve
 * respostas pré-gravadas, mantendo os testes determinísticos e offline.
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
    @DisplayName("Extrai a leitura da forma japonesa que coincide com a superfície")
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
    @DisplayName("Usa o endpoint configurado")
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
    @DisplayName("Retorna vazio quando nenhuma forma coincide exatamente")
    void returnsEmptyWhenNoExactMatch() {
        JishoReadingProvider provider = new JishoReadingProvider(url -> Optional.of(VALID_RESPONSE));

        assertTrue(provider.findReading(new Token("勉強", "名詞")).isEmpty());
    }

    @Test
    @DisplayName("Retorna vazio quando a requisição falha")
    void returnsEmptyWhenFetchFails() {
        JishoReadingProvider provider = new JishoReadingProvider(url -> Optional.empty());

        assertTrue(provider.findReading(new Token("日本語", "名詞")).isEmpty());
    }

    @Test
    @DisplayName("Retorna vazio para JSON malformado ou sem lista de dados")
    void returnsEmptyForMalformedResponse() {
        JishoReadingProvider malformed = new JishoReadingProvider(url -> Optional.of("não é json"));
        assertTrue(malformed.findReading(new Token("日本語", "名詞")).isEmpty());

        JishoReadingProvider withoutData = new JishoReadingProvider(url -> Optional.of("{\"meta\":{}}"));
        assertTrue(withoutData.findReading(new Token("日本語", "名詞")).isEmpty());
    }

    @Test
    @DisplayName("Não consulta o endpoint para token nulo ou em branco")
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
