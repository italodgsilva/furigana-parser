package com.shinji2324.furigana.furigana;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link UsedCodePoints}: coleta do texto principal e das leituras.
 */
class UsedCodePointsTest {

    @Test
    @DisplayName("Coleta os code points do texto principal e das leituras")
    void collectsTextAndReadings() {
        FuriganaEntry entry = new FuriganaEntry("1", "00:00:01,000", "00:00:02,000",
                "日本語",
                List.of(new FuriganaSegment("日本語", "にほんご")));
        FuriganaDocument document = new FuriganaDocument(List.of(entry));

        Set<Integer> codePoints = UsedCodePoints.from(document);

        assertTrue(codePoints.contains((int) '日'));
        assertTrue(codePoints.contains((int) '本'));
        assertTrue(codePoints.contains((int) '語'));
        assertTrue(codePoints.contains((int) 'に'));
        assertTrue(codePoints.contains((int) 'ほ'));
        assertTrue(codePoints.contains((int) 'ん'));
        assertTrue(codePoints.contains((int) 'ご'));
        assertFalse(codePoints.contains((int) 'x'));
    }

    @Test
    @DisplayName("Preserva quebras de linha e ignora leituras ausentes")
    void handlesMissingReadings() {
        FuriganaEntry entry = new FuriganaEntry(null, null, null, "あ\nい",
                List.of(new FuriganaSegment("あ", null)));
        FuriganaDocument document = new FuriganaDocument(List.of(entry));

        Set<Integer> codePoints = UsedCodePoints.from(document);

        assertEquals(Set.of((int) 'あ', (int) '\n', (int) 'い'), codePoints);
    }

    @Test
    @DisplayName("Documento nulo produz conjunto vazio")
    void handlesNullDocument() {
        assertTrue(UsedCodePoints.from(null).isEmpty());
    }
}
