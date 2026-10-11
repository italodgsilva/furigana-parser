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
 * Testes do {@link AssFontEmbedder}: codificação uuencode, montagem da seção
 * {@code [Fonts]} e inserção no ASS.
 */
class AssFontEmbedderTest {

    private static final byte[] OTTO = "OTTO".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("Codifica bytes conhecidos no formato do libass")
    void encodesKnownBytes() {
        assertEquals("!!!!\n", AssFontEmbedder.uuencode(new byte[] {0, 0, 0}, 80));
        assertEquals("4V254Q\n", AssFontEmbedder.uuencode(OTTO, 80));
    }

    @Test
    @DisplayName("Distribui a codificação em linhas e nunca deixa resto 1")
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

        // 256 bytes → 85 grupos de 3 + 1 byte final → 85*4 + 2 = 342 caracteres.
        String flat = encoded.replace("\n", "");
        assertEquals(342, flat.length());
        assertFalse(flat.length() % 4 == 1);
        for (char c : flat.toCharArray()) {
            assertTrue(c >= '!' && c <= '`');
        }
    }

    @Test
    @DisplayName("A codificação volta aos bytes originais, para todos os restos")
    void roundTripsEveryRemainder() {
        for (int length = 0; length <= 10; length++) {
            byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
                data[i] = (byte) (i * 7 + 1);
            }

            byte[] decoded = decode(AssFontEmbedder.uuencode(data, 80));

            assertArrayEquals(data, decoded, "comprimento " + length);
        }
    }

    @Test
    @DisplayName("Monta a seção [Fonts] com o cabeçalho fontname")
    void buildsFontsSection() {
        String section = AssFontEmbedder.section(List.of(new FontFile("MinhaFonte.ttf", OTTO)));

        assertEquals("[Fonts]\nfontname: MinhaFonte.ttf\n4V254Q\n", section);
    }

    @Test
    @DisplayName("Insere a seção [Fonts] antes de [Events]")
    void insertsSectionBeforeEvents() {
        String ass = "[Script Info]\nTitle: X\n\n[V4+ Styles]\nStyle: Main,F,...\n\n"
                + "[Events]\nFormat: Layer, Start, End, Style, Text\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,Main,,0,0,0,,ola\n";

        String result = new AssFontEmbedder().embed(ass, List.of(new FontFile("F.ttf", OTTO)));

        int fonts = result.indexOf("[Fonts]");
        int events = result.indexOf("[Events]");
        assertTrue(fonts > 0 && fonts < events);
        assertTrue(result.contains("fontname: F.ttf"));
        assertTrue(result.contains("Dialogue: 0,0:00:01.00,0:00:02.00,Main,,0,0,0,,ola"));
        assertTrue(result.endsWith("ola\n"));
    }

    @Test
    @DisplayName("Sem fontes o ASS não muda")
    void keepsAssUnchangedWithoutFonts() {
        String ass = "[Events]\nFormat: Layer\n";

        assertEquals(ass, new AssFontEmbedder().embed(ass, List.of()));
        assertEquals(ass, new AssFontEmbedder().embed(ass, null));
        assertEquals(null, new AssFontEmbedder().embed(null, List.of(new FontFile("F.ttf", OTTO))));
    }

    @Test
    @DisplayName("Sem seção [Events], anexa a seção ao final")
    void appendsWhenNoEventsSection() {
        String result = new AssFontEmbedder().embed("[Script Info]\nTitle: X\n",
                List.of(new FontFile("F.ttf", OTTO)));

        assertTrue(result.contains("[Fonts]"));
        assertTrue(result.contains("fontname: F.ttf"));
    }

    /** Decodificador equivalente ao do libass, usado para validar a ida e volta. */
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
