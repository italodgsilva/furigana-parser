package com.shinji2324.furigana.japanese;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ScriptJapaneseTokenizer} (segmentation by script).
 */
class JapaneseTokenizerTest {

    private final JapaneseTokenizer tokenizer = new ScriptJapaneseTokenizer();

    @Test
    @DisplayName("Segments a Japanese sentence into script runs")
    void tokenizesSentenceIntoScriptRuns() {
        List<Token> tokens = tokenizer.tokenize("今日は日本語を勉強します。");

        assertEquals(List.of("今日", "は", "日本語", "を", "勉強", "します", "。"),
                tokens.stream().map(Token::getSurface).toList());
    }

    @Test
    @DisplayName("Classifies kanji, hiragana, katakana, and punctuation tokens")
    void detectsScriptKinds() {
        List<Token> tokens = tokenizer.tokenize("今日かなカタ。");

        assertEquals("KANJI", tokens.get(0).getKind());
        assertEquals("HIRAGANA", tokens.get(1).getKind());
        assertEquals("KATAKANA", tokens.get(2).getKind());
        assertEquals("PUNCTUATION", tokens.get(3).getKind());
    }

    @Test
    @DisplayName("Ignores whitespace")
    void ignoresWhitespace() {
        List<Token> tokens = tokenizer.tokenize("今日 は\n日本語");

        assertEquals(List.of("今日", "は", "日本語"), tokens.stream().map(Token::getSurface).toList());
    }

    @Test
    @DisplayName("Preserves the offsets of each token in the original text")
    void keepsOffsets() {
        List<Token> tokens = tokenizer.tokenize("今日は日本語");

        assertEquals(0, tokens.get(0).getStart());
        assertEquals(2, tokens.get(0).getEnd());
        assertEquals(2, tokens.get(1).getStart());
        assertEquals(3, tokens.get(1).getEnd());
        assertEquals(3, tokens.get(2).getStart());
        assertEquals(6, tokens.get(2).getEnd());
    }

    @Test
    @DisplayName("Preserves unrecognized characters as UNKNOWN tokens")
    void preservesUnknownCharacters() {
        List<Token> tokens = tokenizer.tokenize("ＡＢＣ"); // full-width Latin letters

        assertEquals(3, tokens.size());
        assertTrue(tokens.stream().allMatch(token -> "UNKNOWN".equals(token.getKind())));
        assertEquals("Ａ", tokens.get(0).getSurface());
    }

    @Test
    @DisplayName("Returns an empty list for null or blank text")
    void returnsEmptyListForNullOrBlank() {
        assertTrue(tokenizer.tokenize(null).isEmpty());
        assertTrue(tokenizer.tokenize("   ").isEmpty());
    }

    @Test
    @DisplayName("Classifies Latin groups as single tokens")
    void tokenizesLatinGroupsAsSingleToken() {
        List<Token> tokens = tokenizer.tokenize("DVDを観る");

        assertEquals("DVD", tokens.get(0).getSurface());
        assertEquals("UNKNOWN", tokens.get(0).getKind());
    }
}