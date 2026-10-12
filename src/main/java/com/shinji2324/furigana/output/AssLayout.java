package com.shinji2324.furigana.output;

/**
 * On-screen furigana geometry, used to position each reading over the kanji.
 *
 * <p>Since ASS has no native ruby, {@link AssGenerator} positions each reading
 * with the {@code \pos} tag, computing the horizontal coordinate from a character
 * grid. Each full-width character (kanji, kana, Japanese punctuation) occupies one
 * "em"; half-width katakana occupies half an "em"; and ASCII characters use the
 * real advance of the template font (table {@code ASCII_ADVANCES}). This way the
 * alignment is precise for Japanese and for the Latin present in the subtitles, as
 * long as the template font is used.
 *
 * <p><strong>The "em" is not the font size declared in the ASS.</strong> libass
 * (mpv's subtitle engine) interprets the ASS {@code Fontsize} as the <em>line
 * height</em> of the font — the ascender minus the descender of the {@code hhea}
 * table —, and not as the "em" size. That is, the real advance of a full-width
 * character is {@code Fontsize * EM_PER_FONT_SIZE}, with {@code EM_PER_FONT_SIZE}
 * depending on the font. Ignoring this factor shifts all readings horizontally
 * (the error grows with the line length).
 * <p>The vertical coordinates of the main text use the line box (libass sizes it
 * exactly to the {@code Fontsize}); furigana, however, is positioned by the "em"
 * (the glyph boxes), because this font's line box has a large internal "leading"
 * that would leave the reading far from the kanji.
 *
 * <p>The constants assume the 1920x1080 resolution and the font
 * {@code Noto Sans CJK JP}, both declared in {@code /ass/default-style.ass}. All
 * coordinates use {@code \an5} alignment (center), so that the {@code (x, y)} pair
 * is the center of the text.
 */
public final class AssLayout {

    /** Logical screen width (must match the template's PlayResX). */
    public static final int PLAY_RES_X = 1920;

    /** Logical screen height (must match the template's PlayResY). */
    public static final int PLAY_RES_Y = 1080;

    /** Main text font size (must match the Main style). */
    public static final int MAIN_FONT_SIZE = 64;

    /** Furigana font size (must match the Furigana style). */
    public static final int FURIGANA_FONT_SIZE = 32;

    /** Vertical gap between the main text "em" and the furigana "em", in pixels. */
    public static final int FURIGANA_GAP = 4;

    /** Vertical distance between consecutive main text lines. */
    public static final int LINE_HEIGHT = 84;

    /** Bottom margin reserved for the last main text line. */
    public static final int MAIN_BOTTOM_MARGIN = 60;

    /**
     * Factor that converts the font size declared in the ASS to the real width of
     * a full-width character (one "em"), as rendered by libass.
     *
     * <p>libass sizes the font so that the ASS {@code Fontsize} is the line height
     * ({@code hhea.ascender - hhea.descender}), and not the "em" size. For the
     * template font ({@code Noto Sans CJK JP}):
     * {@code unitsPerEm = 1000}, {@code hhea.ascender = 1160}, and
     * {@code hhea.descender = -288}, so the advance of a full-width character is
     * {@code 1000 / (1160 + 288) = 1000/1448 ≈ 0.6906} of the {@code Fontsize}.
     * Kanji and kana have an advance of exactly one "em".
     *
     * <p><strong>Warning:</strong> this value is specific to the template font.
     * Changing the font requires recomputing the factor from the {@code hhea}
     * metrics of the new font, otherwise the furigana will be misaligned.
     */
    public static final double EM_PER_FONT_SIZE = 1000.0 / 1448.0;

    /** Width of a full-width character (one "square" of the grid). */
    public static final double CELL_WIDTH = MAIN_FONT_SIZE * EM_PER_FONT_SIZE;

    /**
     * Advance, in "em", of the printable ASCII characters from {@code U+0020} to
     * {@code U+007E}, in the template font.
     *
     * <p>Values read from the {@code hmtx} table of {@code Noto Sans CJK JP}
     * ({@code unitsPerEm = 1000}) and normalized by the "em" (the advance of a
     * full-width character). The font renders Latin proportionally — for example,
     * the space is {@code 0.224} and the {@code "}"} is {@code 0.338} —, far from a
     * fixed half-"em" width. Using the real advances avoids cumulative shifts in
     * lines with punctuation or Latin text.
     *
     * <p><strong>Warning:</strong> the table is specific to the template font.
     * Changing the font requires recomputing it from the {@code hmtx} metrics of
     * the new font.
     */
    private static final double[] ASCII_ADVANCES = {
            0.224, 0.323, 0.474, 0.555, 0.555, 0.921, 0.680, 0.278, // 0x20..0x27
            0.338, 0.338, 0.467, 0.555, 0.278, 0.347, 0.278, 0.392, // 0x28..0x2F
            0.555, 0.555, 0.555, 0.555, 0.555, 0.555, 0.555, 0.555, // 0x30..0x37
            0.555, 0.555, 0.278, 0.278, 0.555, 0.555, 0.555, 0.474, // 0x38..0x3F
            0.946, 0.608, 0.657, 0.638, 0.688, 0.589, 0.552, 0.689, // 0x40..0x47
            0.728, 0.293, 0.535, 0.646, 0.543, 0.812, 0.723, 0.742, // 0x48..0x4F
            0.633, 0.742, 0.635, 0.596, 0.599, 0.721, 0.575, 0.878, // 0x50..0x57
            0.573, 0.531, 0.603, 0.338, 0.392, 0.338, 0.555, 0.559, // 0x58..0x5F
            0.606, 0.563, 0.618, 0.510, 0.620, 0.554, 0.325, 0.564, // 0x60..0x67
            0.607, 0.275, 0.275, 0.552, 0.284, 0.926, 0.610, 0.606, // 0x68..0x6F
            0.620, 0.620, 0.388, 0.468, 0.377, 0.607, 0.521, 0.802, // 0x70..0x77
            0.498, 0.521, 0.475, 0.338, 0.270, 0.338, 0.555, // 0x78..0x7E
    };

    private AssLayout() {
        // Utility class: must not be instantiated.
    }

    /**
     * Vertical coordinate (center) of the main line.
     *
     * @param indexFromBottom distance from the line to the bottom of the block (0 = last line)
     * @return vertical center of the line in pixels
     */
    public static double mainCenterY(int indexFromBottom) {
        double bottomLineCenter = PLAY_RES_Y - MAIN_BOTTOM_MARGIN - MAIN_FONT_SIZE / 2.0;
        return bottomLineCenter - indexFromBottom * LINE_HEIGHT;
    }

    /**
     * Vertical coordinate (center) of the furigana associated with a main line.
     *
     * @param indexFromBottom distance from the line to the bottom of the block (0 = last line)
     * @return vertical center of the furigana in pixels
     */
    public static double furiganaCenterY(int indexFromBottom) {
        // CJK glyphs live in the "em", not in the line box: this font has a
        // line height of 1.448 "em" and a large internal "leading". Positioning
        // the line boxes leaves the furigana far from the word; positioning the
        // "em" (CELL_WIDTH for Main, FURIGANA_FONT_SIZE * EM_PER_FONT_SIZE for
        // Furigana) with a small gap keeps it close to the kanji.
        double offset = CELL_WIDTH / 2.0 + FURIGANA_GAP + FURIGANA_FONT_SIZE * EM_PER_FONT_SIZE / 2.0;
        return mainCenterY(indexFromBottom) - offset;
    }

    /**
     * Computes the horizontal coordinate (center) of a grid cell.
     *
     * @param centerCell center cell of the span, from the start of the line
     * @param totalCells total width of the line, in cells
     * @return horizontal center in pixels, aligned with the centered text
     */
    public static double xForCenterCell(double centerCell, double totalCells) {
        double lineStart = (PLAY_RES_X - totalCells * CELL_WIDTH) / 2.0;
        return lineStart + centerCell * CELL_WIDTH;
    }

    /**
     * Sums the width, in cells, of a text.
     *
     * @param text text to measure (may be {@code null})
     * @return total width in cells
     */
    public static double cells(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += cellWidth(text.charAt(i));
        }
        return total;
    }

    /**
     * Width of a character in the grid, in "em" units (full-width).
     *
     * <p>ASCII characters use the real advance of the template font
     * ({@link #ASCII_ADVANCES}); some Latin signs applicable to subtitles (curly
     * quotes and dashes) as well; half-width katakana and half-width punctuation
     * occupy half an "em"; the rest (kanji, kana, Japanese punctuation) occupy one
     * "em". Control characters do not shift the grid.
     *
     * @param c character to measure
     * @return width in "em"
     */
    public static double cellWidth(char c) {
        if (c < ' ' || c == '\u007F') {
            // Controls (e.g. a leftover '\r') should not exist in the text.
            return 0.0;
        }
        if (c <= '~') {
            return ASCII_ADVANCES[c - ' '];
        }
        switch (c) {
            case '\u2018': // ‘
            case '\u2019': // ’
                return 0.278;
            case '\u201C': // “
            case '\u201D': // ”
                return 0.474;
            case '\u2013': // –
                return 0.536;
            case '\u2014': // —
                return 0.894;
            case '\u2E3A': // ⸺
                return 1.676;
            default:
                break;
        }
        if (c >= '\uFF61' && c <= '\uFF9F') {
            // Half-width katakana and half-width punctuation.
            return 0.5;
        }
        return 1.0;
    }
}
