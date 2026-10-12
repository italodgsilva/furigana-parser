package com.shinji2324.furigana.furigana;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link UsedCodePoints}: collection of the main text and the readings.
 */
class UsedCodePointsTest {

    @Test
    @DisplayName("Collects the code points of the main text and the readings")
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
    @DisplayName("Preserves line breaks and ignores missing readings")
    void handlesMissingReadings() {
        FuriganaEntry entry = new FuriganaEntry(null, null, null, "あ\nい",
                List.of(new FuriganaSegment("あ", null)));
        FuriganaDocument document = new FuriganaDocument(List.of(entry));

        Set<Integer> codePoints = UsedCodePoints.from(document);

        assertEquals(Set.of((int) 'あ', (int) '\n', (int) 'い'), codePoints);
    }

    @Test
    @DisplayName("Null document produces an empty set")
    void handlesNullDocument() {
        assertTrue(UsedCodePoints.from(null).isEmpty());
    }
}
