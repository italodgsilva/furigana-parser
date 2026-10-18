package com.shinji2324.furigana.core;

import java.nio.file.Path;

/**
 * Options that drive a furigana generation job, independent of the user
 * interface (CLI, GUI, tests).
 *
 * <p>{@code stripParentheses} removes the passages between round parentheses from
 * the text before furigana and applies to both output formats. {@code embedFont}
 * enables font embedding in the ASS. {@code subsetFont}, when enabled, subsets
 * the embedded font to the glyphs used in the subtitle (requires
 * {@code embedFont}). {@code fontPath}, when provided, points to the font file to
 * embed; when {@code null}, the font is located by family name on the system.
 * {@code color}, when provided, sets the primary color of the {@code Main} and
 * {@code Furigana} styles.
 *
 * @param stripParentheses whether the passages between parentheses should be removed
 * @param embedFont        whether the font should be embedded in the ASS
 * @param subsetFont       whether the embedded font should be subset to the glyphs used
 * @param fontPath         font file to embed, or {@code null} to locate it
 * @param color            primary color of the styles, or {@code null} for the default
 */
public record FuriganaOptions(boolean stripParentheses, boolean embedFont, boolean subsetFont,
                              Path fontPath, String color) {

    /**
     * Returns the default options: no parenthesis stripping, no font embedding
     * and the default color.
     *
     * @return default options
     */
    public static FuriganaOptions defaults() {
        return new FuriganaOptions(false, false, false, null, null);
    }
}
