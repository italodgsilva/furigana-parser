package com.shinji2324.furigana.japanese;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link KuromojiTokenizer} (morphological tokenization).
 *
 * <p>The expectations reflect the IPADIC dictionary's analysis for the example
 * sentences; dictionary changes may require adjustments.
 */
class KuromojiTokenizerTest {

    private final JapaneseTokenizer tokenizer = new KuromojiTokenizer();

    @Test
    @DisplayName("Segments a Japanese sentence morphologically")
    void tokenizesSentenceMorphologically() {
        List<String> surfaces = tokenizer.tokenize("今日は日本語を勉強します。").stream()
                .map(Token::getSurface)
                .toList();

        assertEquals(List.of("今日", "は", "日本語", "を", "勉強", "し", "ます", "。"), surfaces);
    }

    @Test
    @DisplayName("Provides readings normalized to hiragana")
    void providesHiraganaReadings() {
        List<Token> tokens = tokenizer.tokenize("今日は日本語を勉強します。");

        assertEquals("きょう", surface(tokens, "今日").getReading());
        assertEquals("は", surface(tokens, "は").getReading());
        assertEquals("にほんご", surface(tokens, "日本語").getReading());
        assertEquals("べんきょう", surface(tokens, "勉強").getReading());
    }

    @Test
    @DisplayName("Classifies the token by part of speech")
    void usesPartOfSpeechAsKind() {
        List<Token> tokens = tokenizer.tokenize("日本語を");

        assertEquals("名詞", surface(tokens, "日本語").getKind());
        assertEquals("助詞", surface(tokens, "を").getKind());
    }

    @Test
    @DisplayName("Preserves the offsets in the tokenized text")
    void keepsOffsets() {
        List<Token> tokens = tokenizer.tokenize("今日は日本語");

        Token nihongo = surface(tokens, "日本語");
        assertEquals(3, nihongo.getStart());
        assertEquals(6, nihongo.getEnd());
    }

    @Test
    @DisplayName("Unknown reading (numbers/Latin) is null")
    void unknownReadingIsNull() {
        List<Token> tokens = tokenizer.tokenize("123円");

        assertNull(surface(tokens, "123").getReading());
        assertEquals("えん", surface(tokens, "円").getReading());
    }

    @Test
    @DisplayName("Splits the verb into stem and okurigana already during tokenization")
    void tokenizesConjugatedVerb() {
        List<Token> tokens = tokenizer.tokenize("食べる");

        assertEquals(List.of("食べる"), tokens.stream().map(Token::getSurface).toList());
        assertEquals("たべる", tokens.get(0).getReading());
    }

    @Test
    @DisplayName("Returns an empty list for null or blank text")
    void returnsEmptyForNullOrBlank() {
        assertTrue(tokenizer.tokenize(null).isEmpty());
        assertTrue(tokenizer.tokenize("   ").isEmpty());
    }

    private Token surface(List<Token> tokens, String surface) {
        return tokens.stream()
                .filter(token -> surface.equals(token.getSurface()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("token not found: " + surface));
    }
}
