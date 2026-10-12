package com.shinji2324.furigana.output;

import com.shinji2324.furigana.font.FontFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AssFontEmbedder}: uuencode encoding, assembly of the
 * {@code [Fonts]} section and insertion into the ASS.
 */
class AssFontEmbedderTest {

    private static final byte[] OTTO = "OTTO".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("Encodes known bytes in the libass format")
    void encodesKnownBytes() {
        assertEquals("!!!!\n", AssFontEmbedder.uuencode(new byte[] {0, 0, 0}, 80));
        assertEquals("4V254Q\n", AssFontEmbedder.uuencode(OTTO, 80));
    }

    @Test
    @DisplayName("Splits the encoding into lines and never leaves a remainder of 1")
    void splitsLinesWithoutRemainderOne() {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        String encoded = AssFontEmbedder.uuencode(data, 16);
        String[] lines = encoded.split("\n");

        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(16, lines[i].length());
        }
        assertTrue(lines[lines.length - 1].length() >= 1);
        assertTrue(lines[lines.length - 1].length() <= 16);

        // 256 bytes → 85 groups of 3 + 1 final byte → 85*4 + 2 = 342 characters.
        String flat = encoded.replace("\n", "");
        assertEquals(342, flat.length());
        assertFalse(flat.length() % 4 == 1);
        for (char c : flat.toCharArray()) {
            assertTrue(c >= '!' && c <= '`');
        }
    }

    @Test
    @DisplayName("The encoding round-trips to the original bytes, for every remainder")
    void roundTripsEveryRemainder() {
        for (int length = 0; length <= 10; length++) {
            byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
                data[i] = (byte) (i * 7 + 1);
            }

            byte[] decoded = decode(AssFontEmbedder.uuencode(data, 80));

            assertArrayEquals(data, decoded, "length " + length);
        }
    }

    @Test
    @DisplayName("Builds the [Fonts] section with the fontname header")
    void buildsFontsSection() {
        String section = AssFontEmbedder.section(List.of(new FontFile("MyFont.ttf", OTTO)));

        assertEquals("[Fonts]\nfontname: MyFont.ttf\n4V254Q\n", section);
    }

    @Test
    @DisplayName("Inserts the [Fonts] section before [Events]")
    void insertsSectionBeforeEvents() {
        String ass = "[Script Info]\nTitle: X\n\n[V4+ Styles]\nStyle: Main,F,...\n\n"
                + "[Events]\nFormat: Layer, Start, End, Style, Text\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,Main,,0,0,0,,hello\n";

        String result = new AssFontEmbedder().embed(ass, List.of(new FontFile("F.ttf", OTTO)));

        int fonts = result.indexOf("[Fonts]");
        int events = result.indexOf("[Events]");
        assertTrue(fonts > 0 && fonts < events);
        assertTrue(result.contains("fontname: F.ttf"));
        assertTrue(result.contains("Dialogue: 0,0:00:01.00,0:00:02.00,Main,,0,0,0,,hello"));
        assertTrue(result.endsWith("hello\n"));
    }

    @Test
    @DisplayName("Without fonts the ASS is unchanged")
    void keepsAssUnchangedWithoutFonts() {
        String ass = "[Events]\nFormat: Layer\n";

        assertEquals(ass, new AssFontEmbedder().embed(ass, List.of()));
        assertEquals(ass, new AssFontEmbedder().embed(ass, null));
        assertEquals(null, new AssFontEmbedder().embed(null, List.of(new FontFile("F.ttf", OTTO))));
    }

    @Test
    @DisplayName("Without an [Events] section, appends the section at the end")
    void appendsWhenNoEventsSection() {
        String result = new AssFontEmbedder().embed("[Script Info]\nTitle: X\n",
                List.of(new FontFile("F.ttf", OTTO)));

        assertTrue(result.contains("[Fonts]"));
        assertTrue(result.contains("fontname: F.ttf"));
    }

    /** Decoder equivalent to libass's, used to validate the round trip. */
    private static byte[] decode(String encoded) {
        String flat = encoded.replace("\n", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int index = 0;
        while (index < flat.length()) {
            int count = Math.min(4, flat.length() - index);
            long value = 0;
            for (int k = 0; k < count; k++) {
                value |= (long) ((flat.charAt(index + k) - 33) & 63) << (6 * (3 - k));
            }
            out.write((int) (value >> 16));
            if (count >= 3) {
                out.write((int) (value >> 8) & 0xFF);
            }
            if (count >= 4) {
                out.write((int) value & 0xFF);
            }
            index += count;
        }
        return out.toByteArray();
    }
}
