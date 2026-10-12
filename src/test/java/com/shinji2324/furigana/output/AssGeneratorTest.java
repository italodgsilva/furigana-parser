package com.shinji2324.furigana.output;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AssGenerator}: header, main text events and furigana
 * positioning.
 */
class AssGeneratorTest {

    private final AssGenerator generator = new AssGenerator();

    @Test
    @DisplayName("Loads the default template with the Main and Furigana styles")
    void loadsDefaultTemplate() {
        String content = AssTemplate.defaultTemplate().content();

        assertTrue(content.contains("[Script Info]"));
        assertTrue(content.contains("PlayResX: 1920"));
        assertTrue(content.contains("Style: Main,"));
        assertTrue(content.contains("Style: Furigana,"));
        assertTrue(content.contains("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"));
    }

    @Test
    @DisplayName("Generates the main text and positioned furigana for one line")
    void generatesSingleLineWithPositionedFurigana() {
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "1", "00:00:01,000", "00:00:04,500",
                "今日は日本語を勉強します。",
                List.of(
                        new FuriganaSegment("今日", "きょう"),
                        new FuriganaSegment("は", null),
                        new FuriganaSegment("日本語", "にほんご"),
                        new FuriganaSegment("を", null),
                        new FuriganaSegment("勉強", "べんきょう"),
                        new FuriganaSegment("し", null),
                        new FuriganaSegment("ます", null),
                        new FuriganaSegment("。", null)))));

        String output = generator.generate(document);

        // Main text: a single line, centered on screen, at the bottom.
        assertTrue(output.contains(
                "Dialogue: 0,0:00:01.00,0:00:04.50,Main,,0,0,0,,{\\an5\\pos(960,988)}今日は日本語を勉強します。"));

        // Furigana aligned over each kanji block (13 cells, starting at 672.7px).
        assertTrue(output.contains("Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\\an5\\pos(717,951)}きょう"));
        assertTrue(output.contains("Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\\an5\\pos(872,951)}にほんご"));
        assertTrue(output.contains("Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\\an5\\pos(1026,951)}べんきょう"));

        // Only the three kanji blocks generate furigana.
        assertEquals(3, countOccurrences(output, ",Furigana,"));
        assertEquals(1, countOccurrences(output, ",Main,"));
    }

    @Test
    @DisplayName("Handles two-line entries, associating the furigana with the correct line")
    void handlesTwoLineEntry() {
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "2", "00:00:05,000", "00:00:07,000",
                "元気ですか？\n元気です。",
                List.of(
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("ですか？", null),
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("です。", null)))));

        String output = generator.generate(document);

        // Two main lines: the top one at 904 and the bottom one at 988.
        assertTrue(output.contains("{\\an5\\pos(960,904)}元気ですか？"));
        assertTrue(output.contains("{\\an5\\pos(960,988)}元気です。"));
        // The furigana follows the line: y=867 (top) and y=951 (bottom).
        // Line 1 (6 cells, starting at 827.4px): center of "元気" at 872.
        // Line 2 (5 cells, starting at 849.5px): center of "元気" at 894.
        assertTrue(output.contains(",Furigana,,0,0,0,,{\\an5\\pos(872,867)}げんき"));
        assertTrue(output.contains(",Furigana,,0,0,0,,{\\an5\\pos(894,951)}げんき"));
        assertEquals(2, countOccurrences(output, ",Main,"));
        assertEquals(2, countOccurrences(output, ",Furigana,"));
    }

    @Test
    @DisplayName("Ignores the line-break segment when associating furigana (Kuromoji)")
    void handlesNewlineSegment() {
        // Kuromoji emits the line break as a segment without a reading;
        // it must not interrupt the association of the next line's segments.
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "2", "00:00:05,000", "00:00:07,000",
                "元気ですか？\n私は元気です。",
                List.of(
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("です", null),
                        new FuriganaSegment("か", null),
                        new FuriganaSegment("？", null),
                        new FuriganaSegment("\n", null),
                        new FuriganaSegment("私", "わたし"),
                        new FuriganaSegment("は", null),
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("です", null),
                        new FuriganaSegment("。", null)))));

        String output = generator.generate(document);

        // "私" and "元気" from the second line must generate furigana
        // (in addition to "元気" from the first).
        assertTrue(output.contains(",Furigana,,0,0,0,,{\\an5\\pos("));
        assertTrue(output.contains("}わたし"));
        assertTrue(output.contains("}げんき"));
        assertEquals(2, countOccurrences(output, ",Main,"));
        assertEquals(3, countOccurrences(output, ",Furigana,"));
    }

    @Test
    @DisplayName("Does not generate furigana for kana-only text")
    void doesNotGenerateFuriganaForKanaOnly() {
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "3", "00:00:08,000", "00:00:09,000",
                "ありがとう。",
                List.of(
                        new FuriganaSegment("ありがとう", null),
                        new FuriganaSegment("。", null)))));

        String output = generator.generate(document);

        assertEquals(1, countOccurrences(output, ",Main,"));
        assertEquals(0, countOccurrences(output, ",Furigana,"));
    }

    @Test
    @DisplayName("Generates an empty document with only the header")
    void generatesOnlyHeaderForEmptyDocument() {
        String output = generator.generate(new FuriganaDocument());

        assertTrue(output.contains("[Script Info]"));
        assertFalse(output.contains("Dialogue:"));
    }

    @Test
    @DisplayName("Accepts a null document without breaking")
    void toleratesNullDocument() {
        String output = generator.generate(null);

        assertTrue(output.contains("[Script Info]"));
        assertFalse(output.contains("Dialogue:"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
