package com.shinji2324.furigana.subtitle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SrtParser}.
 */
class SrtParserTest {

    private final SrtParser parser = new SrtParser();

    // ------------------------------------------------------------------
    // Example content (fixtures)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Parsing a simple ASCII subtitle")
    void parsesSimpleFixture() throws IOException {
        Subtitle subtitle = parser.parse(readFixture("simple.srt"));

        assertEquals(2, subtitle.size());
        List<SubtitleEntry> entries = subtitle.getEntries();

        SubtitleEntry first = entries.get(0);
        assertEquals("1", first.getId());
        assertEquals("00:00:01,000", first.getStart());
        assertEquals("00:00:02,500", first.getEnd());
        assertEquals("Hello, world!", first.getText());

        SubtitleEntry second = entries.get(1);
        assertEquals("2", second.getId());
        assertEquals("Second line, still ASCII.", second.getText());
    }

    @Test
    @DisplayName("Parsing a Japanese subtitle with multiple text lines")
    void parsesJapaneseFixture() throws IOException {
        Subtitle subtitle = parser.parse(readFixture("japanese.srt"));

        assertEquals(3, subtitle.size());
        List<SubtitleEntry> entries = subtitle.getEntries();

        assertEquals("今日は日本語を勉強します。", entries.get(0).getText());

        // Entry with two text lines joined by \n.
        assertEquals("元気ですか？\n私は元気です。", entries.get(1).getText());

        // Entry that ends without a newline at the end of the file.
        assertEquals("これは長いセリフで、\n二行目に続きます。", entries.get(2).getText());
    }

    // ------------------------------------------------------------------
    // Accepted format variations
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Accepts an entry without a numeric index")
    void parsesEntryWithoutIndex() {
        String content = """
                00:00:01,000 --> 00:00:02,000
                Text without index
                """;

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        SubtitleEntry entry = subtitle.getEntries().get(0);
        assertNull(entry.getId());
        assertEquals("Text without index", entry.getText());
    }

    @Test
    @DisplayName("Recognizes a timestamp separator with extra spaces")
    void acceptsExtraSpacesAroundTimestampArrow() {
        String content = "1\n00:00:01,000   -->   00:00:02,000\nHello!";

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        SubtitleEntry entry = subtitle.getEntries().get(0);
        assertEquals("00:00:01,000", entry.getStart());
        assertEquals("00:00:02,000", entry.getEnd());
    }

    @Test
    @DisplayName("Accepts a timestamp with a dot instead of a comma")
    void acceptsTimestampWithDotSeparator() {
        String content = "1\n00:00:01.500 --> 00:00:02.750\nDecimal point";

        Subtitle subtitle = parser.parse(content);

        assertEquals("00:00:01.500", subtitle.getEntries().get(0).getStart());
        assertEquals("00:00:02.750", subtitle.getEntries().get(0).getEnd());
    }

    @Test
    @DisplayName("Preserves the comma and the precision of the original timestamp")
    void preservesTimestampAsWritten() {
        String content = "1\n00:00:12,345 --> 00:01:13,999\nTest";

        SubtitleEntry entry = parser.parse(content).getEntries().get(0);

        assertEquals("00:00:12,345", entry.getStart());
        assertEquals("00:01:13,999", entry.getEnd());
    }

    @Test
    @DisplayName("Joins multiple text lines using \\n")
    void concatenatesMultilineTextWithNewline() {
        String content = "1\n00:00:01,000 --> 00:00:02,000\nFirst\nSecond\nThird";

        SubtitleEntry entry = parser.parse(content).getEntries().get(0);

        assertEquals("First\nSecond\nThird", entry.getText());
    }

    @Test
    @DisplayName("Preserves structural spaces in the subtitle text")
    void preservesStructuralSpacesInText() {
        String content = "1\n00:00:01,000 --> 00:00:02,000\n  indented line  ";

        SubtitleEntry entry = parser.parse(content).getEntries().get(0);

        assertEquals("  indented line  ", entry.getText());
    }

    @Test
    @DisplayName("Ignores blank lines between blocks")
    void ignoresBlankLinesBetweenBlocks() {
        String content = """
                1
                00:00:01,000 --> 00:00:02,000
                First

                   \s


                2
                00:00:03,000 --> 00:00:04,000
                Second
                """;

        Subtitle subtitle = parser.parse(content);

        assertEquals(2, subtitle.size());
        assertEquals("Second", subtitle.getEntries().get(1).getText());
    }

    @Test
    @DisplayName("Ignores a single line of noise at the end of the file")
    void skipsSingleLineJunkAtEnd() {
        String content = """
                1
                00:00:01,000 --> 00:00:02,000
                Valid text

                footer
                """;

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
    }

    // ------------------------------------------------------------------
    // Line endings and encoding
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Accepts CRLF line breaks")
    void handlesCrlfLineEndings() {
        String content = "1\r\n00:00:01,000 --> 00:00:02,000\r\nText with CRLF\r\n\r\n"
                + "2\r\n00:00:03,000 --> 00:00:04,000\r\nSecond\r\n";

        Subtitle subtitle = parser.parse(content);

        assertEquals(2, subtitle.size());
        assertEquals("Text with CRLF", subtitle.getEntries().get(0).getText());
        assertEquals("Second", subtitle.getEntries().get(1).getText());
    }

    @Test
    @DisplayName("Accepts CR line breaks (classic Mac)")
    void handlesCrLineEndings() {
        String content = "1\r00:00:01,000 --> 00:00:02,000\rText with CR\r\r2\r00:00:03,000 --> 00:00:04,000\rSecond\r";

        Subtitle subtitle = parser.parse(content);

        assertEquals(2, subtitle.size());
        assertEquals("Text with CR", subtitle.getEntries().get(0).getText());
    }

    @Test
    @DisplayName("Removes the UTF-8 BOM at the start of the content")
    void handlesBomAtStartOfContent() {
        String content = "\uFEFF1\n00:00:01,000 --> 00:00:02,000\nWith BOM";

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        assertEquals("With BOM", subtitle.getEntries().get(0).getText());
    }

    @Test
    @DisplayName("Accepts a trailing newline after the last block")
    void handlesTrailingNewline() {
        String content = "1\n00:00:01,000 --> 00:00:02,000\nText\n";

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        assertEquals("Text", subtitle.getEntries().get(0).getText());
    }

    // ------------------------------------------------------------------
    // Empty content
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Returns an empty subtitle for null content")
    void returnsEmptySubtitleForNull() {
        assertTrue(parser.parse(null).getEntries().isEmpty());
    }

    @Test
    @DisplayName("Returns an empty subtitle for blank content")
    void returnsEmptySubtitleForBlank() {
        assertTrue(parser.parse("   \n  ").getEntries().isEmpty());
        assertTrue(parser.parse("").getEntries().isEmpty());
    }

    // ------------------------------------------------------------------
    // Invalid content (fail-fast)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Throws an exception when the block has no timestamp")
    void throwsOnBlockWithoutTimestamp() {
        String content = "2\nthis block has no timestamp\ncontinuation";

        SubtitleParseException exception = assertThrows(SubtitleParseException.class, () -> parser.parse(content));
        assertNotNull(exception.getMessage());
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    /**
     * Reads a subtitle fixture from the test classpath (directory {@code /subtitles}).
     */
    private String readFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/subtitles/" + name)) {
            assertNotNull(in, "Fixture not found on the classpath: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}