package com.shinji2324.furigana.japanese;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link ScriptJapaneseTokenizer} (tokenização por escrita).
 */
class JapaneseTokenizerTest {

    private final JapaneseTokenizer tokenizer = new ScriptJapaneseTokenizer();

    @Test
    @DisplayName("Segmenta uma frase japonesa por sequências de escrita")
    void tokenizesSentenceIntoScriptRuns() {
        List<Token> tokens = tokenizer.tokenize("今日は日本語を勉強します。");

        assertEquals(List.of("今日", "は", "日本語", "を", "勉強", "します", "。"),
                tokens.stream().map(Token::getSurface).toList());
    }

    @Test
    @DisplayName("Classifica tokens de kanji, hiragana, katakana e pontuação")
    void detectsScriptKinds() {
        List<Token> tokens = tokenizer.tokenize("今日かなカタ。");

        assertEquals("KANJI", tokens.get(0).getKind());
        assertEquals("HIRAGANA", tokens.get(1).getKind());
        assertEquals("KATAKANA", tokens.get(2).getKind());
        assertEquals("PUNCTUATION", tokens.get(3).getKind());
    }

    @Test
    @DisplayName("Ignora espaços em branco")
    void ignoresWhitespace() {
        List<Token> tokens = tokenizer.tokenize("今日 は\n日本語");

        assertEquals(List.of("今日", "は", "日本語"), tokens.stream().map(Token::getSurface).toList());
    }

    @Test
    @DisplayName("Preserva caracteres não reconhecidos como tokens UNKNOWN")
    void preservesUnknownCharacters() {
        List<Token> tokens = tokenizer.tokenize("ＡＢＣ"); // letras latinas de largura total

        assertEquals(3, tokens.size());
        assertTrue(tokens.stream().allMatch(token -> "UNKNOWN".equals(token.getKind())));
        assertEquals("Ａ", tokens.get(0).getSurface());
    }

    @Test
    @DisplayName("Retorna lista vazia para texto nulo ou em branco")
    void returnsEmptyListForNullOrBlank() {
        assertTrue(tokenizer.tokenize(null).isEmpty());
        assertTrue(tokenizer.tokenize("   ").isEmpty());
    }

    @Test
    @DisplayName("Classifica grupos latinos como tokens contínuos")
    void tokenizesLatinGroupsAsSingleToken() {
        List<Token> tokens = tokenizer.tokenize("DVDを観る");

        assertEquals("DVD", tokens.get(0).getSurface());
        assertEquals("UNKNOWN", tokens.get(0).getKind());
    }
}