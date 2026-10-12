package com.shinji2324.furigana.output;

import com.shinji2324.furigana.font.FontFile;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embeds font files into an ASS through the {@code [Fonts]} section.
 *
 * <p>The section is the ASS format's own font-embedding mechanism: each block
 * starts with {@code fontname: <file>} followed by the encoded bytes of the
 * font. When the player encounters the section, it registers these fonts in the
 * system provider, which avoids installing the font on the machine.
 *
 * <p>The encoding is the same one implemented by libass: every 3 bytes become 4
 * characters, with 6 bits per character plus 33 ({@code '!'}). Line breaks are
 * ignored on reading, so the encoding is distributed across lines of fixed
 * length. The total number of characters never leaves a remainder of 1 when
 * divided by 4 — libass rejects that case.
 */
public final class AssFontEmbedder {

    /** Number of encoded characters per line (line breaks are ignored). */
    private static final int LINE_LENGTH = 80;

    /** Header of the events section, where the {@code [Fonts]} section is inserted. */
    private static final Pattern EVENTS_SECTION = Pattern.compile("(?m)^\\[Events\\][ \\t]*\\R?");

    /**
     * Inserts the given fonts into the ASS, before the {@code [Events]} section.
     *
     * @param ass   already assembled ASS content
     * @param fonts fonts to embed
     * @return the ASS with the {@code [Fonts]} section (or the ASS itself, if
     *         there are no fonts)
     */
    public String embed(String ass, List<FontFile> fonts) {
        if (ass == null || fonts == null || fonts.isEmpty()) {
            return ass;
        }
        String section = section(fonts);
        Matcher matcher = EVENTS_SECTION.matcher(ass);
        if (!matcher.find()) {
            return ass.endsWith("\n") ? ass + section : ass + "\n" + section;
        }
        return ass.substring(0, matcher.start()) + section + "\n" + ass.substring(matcher.start());
    }

    /**
     * Builds the {@code [Fonts]} section with all the given fonts.
     *
     * @param fonts fonts to encode
     * @return section text, ending in a newline
     */
    public static String section(List<FontFile> fonts) {
        StringBuilder builder = new StringBuilder("[Fonts]\n");
        for (FontFile font : fonts) {
            builder.append("fontname: ").append(font.fileName()).append('\n');
            builder.append(uuencode(font.data(), LINE_LENGTH));
        }
        return builder.toString();
    }

    /**
     * Encodes bytes in the ASS font format (libass uuencode).
     *
     * @param data       bytes to encode
     * @param lineLength number of characters per line (ignored on reading)
     * @return encoded text, ending in a newline
     */
    static String uuencode(byte[] data, int lineLength) {
        StringBuilder builder = new StringBuilder(data.length / 3 * 4 + data.length / lineLength + 2);
        int column = 0;
        for (int i = 0; i < data.length; i += 3) {
            int remaining = data.length - i;
            int b0 = data[i] & 0xFF;
            int b1 = remaining > 1 ? data[i + 1] & 0xFF : 0;
            int b2 = remaining > 2 ? data[i + 2] & 0xFF : 0;
            int value = (b0 << 16) | (b1 << 8) | b2;
            int chars = Math.min(remaining, 3) + 1; // 3→4, 2→3, 1→2
            for (int k = 0; k < chars; k++) {
                builder.append((char) (((value >>> (18 - 6 * k)) & 63) + 33));
                if (++column == lineLength) {
                    builder.append('\n');
                    column = 0;
                }
            }
        }
        if (column != 0) {
            builder.append('\n');
        }
        return builder.toString();
    }
}
