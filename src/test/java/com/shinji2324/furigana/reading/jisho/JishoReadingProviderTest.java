package com.shinji2324.furigana.reading.jisho;

import com.shinji2324.furigana.japanese.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link JishoReadingProvider}.
 *
 * <p>O provider ainda é um esqueleto: a consulta real ao Jisho será conectada em
 * uma etapa posterior. Estes testes garantem que o contrato atual (retornar vazio
 * para entradas inválidas) seja mantido.
 */
class JishoReadingProviderTest {

    private final JishoReadingProvider provider = new JishoReadingProvider();

    @Test
    @DisplayName("Retorna vazio para token nulo")
    void returnsEmptyForNullToken() {
        assertTrue(provider.findReading(null).isEmpty());
    }

    @Test
    @DisplayName("Retorna vazio para superfície em branco")
    void returnsEmptyForBlankSurface() {
        assertTrue(provider.findReading(new Token("   ", "WHITESPACE")).isEmpty());
    }

    @Test
    @DisplayName("Retorna vazio enquanto a consulta real não é implementada")
    void returnsEmptyForAnyTerm() {
        assertTrue(provider.findReading(new Token("日本語", "KANJI")).isEmpty());
    }
}