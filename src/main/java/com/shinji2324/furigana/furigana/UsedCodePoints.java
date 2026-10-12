package com.shinji2324.furigana.furigana;

import java.util.Set;
import java.util.TreeSet;

/**
 * Gathers the Unicode code points that appear in the processed subtitle.
 *
 * <p>It is the input to subsetting: the embedded font needs only the glyphs
 * that the subtitle actually draws. The main text of each entry (the same used
 * in the events of the {@code Main} style) and the readings of the segments
 * (the text of the events of the {@code Furigana} style) are considered. The
 * override tags ({@code \pos} etc.) are not glyphs and therefore are not
 * included.
 *
 * <p>The set is ordered so that the result does not depend on iteration order.
 */
public final class UsedCodePoints {

    private UsedCodePoints() {
    }

    /**
     * Collects the code points used by the document.
     *
     * @param document processed document (may be {@code null})
     * @return ordered set of code points
     */
    public static Set<Integer> from(FuriganaDocument document) {
        Set<Integer> codePoints = new TreeSet<>();
        if (document == null) {
            return codePoints;
        }
        for (FuriganaEntry entry : document.getEntries()) {
            addCodePoints(codePoints, entry.text());
            for (FuriganaSegment segment : entry.segments()) {
                addCodePoints(codePoints, segment.reading());
            }
        }
        return codePoints;
    }

    private static void addCodePoints(Set<Integer> target, String text) {
        if (text == null) {
            return;
        }
        text.codePoints().forEach(target::add);
    }
}
