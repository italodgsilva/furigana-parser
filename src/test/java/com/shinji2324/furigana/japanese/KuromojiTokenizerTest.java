package com.shinji2324.furigana.japanese;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link KuromojiTokenizer} (tokenização morfológica).
 *
 * <p>As expectativas refletem a análise do dicionário IPADIC para as frases de
 * exemplo; mudanças de dicionário podem exigir ajuste.
 */
class KuromojiTokenizerTest {

    private final JapaneseTokenizer tokenizer = new KuromojiTokenizer();

    @Test
    @DisplayName("Segmenta morfologicamente uma frase japonesa")
    void tokenizesSentenceMorphologically() {
        List<String> surfaces = tokenizer.tokenize("今日は日本語を勉強します。").stream()
                .map(Token::getSurface)
                .toList();

        assertEquals(List.of("今日", "は", "日本語", "を", "勉強", "し", "ます", "。"), surfaces);
    }

    @Test
    @DisplayName("Fornece leituras normalizadas para hiragana")
    void providesHiraganaReadings() {
        List<Token> tokens = tokenizer.tokenize("今日は日本語を勉強します。");

        assertEquals("きょう", surface(tokens, "今日").getReading());
        assertEquals("は", surface(tokens, "は").getReading());
        assertEquals("にほんご", surface(tokens, "日本語").getReading());
        assertEquals("べんきょう", surface(tokens, "勉強").getReading());
    }

    @Test
    @DisplayName("Classifica o token pela classe gramatical")
    void usesPartOfSpeechAsKind() {
        List<Token> tokens = tokenizer.tokenize("日本語を");

        assertEquals("名詞", surface(tokens, "日本語").getKind());
        assertEquals("助詞", surface(tokens, "を").getKind());
    }

    @Test
    @DisplayName("Preserva os deslocamentos no texto tokenizado")
    void keepsOffsets() {
        List<Token> tokens = tokenizer.tokenize("今日は日本語");

        Token nihongo = surface(tokens, "日本語");
        assertEquals(3, nihongo.getStart());
        assertEquals(6, nihongo.getEnd());
    }

    @Test
    @DisplayName("Leitura desconhecida (números/latim) fica nula")
    void unknownReadingIsNull() {
        List<Token> tokens = tokenizer.tokenize("123円");

        assertNull(surface(tokens, "123").getReading());
        assertEquals("えん", surface(tokens, "円").getReading());
    }

    @Test
    @DisplayName("Separa o verbo em radical e okurigana já na tokenização")
    void tokenizesConjugatedVerb() {
        List<Token> tokens = tokenizer.tokenize("食べる");

        assertEquals(List.of("食べる"), tokens.stream().map(Token::getSurface).toList());
        assertEquals("たべる", tokens.get(0).getReading());
    }

    @Test
    @DisplayName("Retorna lista vazia para texto nulo ou em branco")
    void returnsEmptyForNullOrBlank() {
        assertTrue(tokenizer.tokenize(null).isEmpty());
        assertTrue(tokenizer.tokenize("   ").isEmpty());
    }

    private Token surface(List<Token> tokens, String surface) {
        return tokens.stream()
                .filter(token -> surface.equals(token.getSurface()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("token não encontrado: " + surface));
    }
}
