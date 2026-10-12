package com.shinji2324.furigana.font;

import java.util.Set;

/**
 * Reduces a font to the glyphs needed for a set of code points.
 *
 * <p>Subsetting is an optional step between locating the font ({@link
 * FontLocator}) and embedding it into the ASS ({@code AssFontEmbedder}).
 * Embedding does not need to know the algorithm: it only has to receive the
 * already reduced bytes. The available implementation uses HarfBuzz ({@link
 * HarfBuzzSubsetter}) and the pipeline falls back to the full font when it is
 * not available.
 */
public interface FontSubsetter {

    /**
     * Reduces the given font to the glyphs used by the code points.
     *
     * @param data       bytes of a single sfnt face ({@code .ttf}/{@code .otf})
     * @param codePoints Unicode code points to keep (text and readings)
     * @return the reduced font bytes, or {@code null} when it was not possible
     *         to reduce — in that case the original font must be used
     */
    byte[] subset(byte[] data, Set<Integer> codePoints);
}
