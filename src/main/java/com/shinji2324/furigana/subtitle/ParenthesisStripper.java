package com.shinji2324.furigana.subtitle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes passages between round parentheses from the text of a subtitle.
 *
 * <p>Japanese subtitles often mark, between parentheses, character names,
 * alternative readings, scene indications or reactions (e.g. {@code （笑）}). Since
 * this content is not part of the speech, it is removed — along with the
 * parentheses themselves — before tokenization, so as not to generate furigana or
 * reading queries.
 *
 * <p>Recognized pairs: full-width {@code （…）} and half-width {@code (…)}. Both
 * sides of a pair must be of the same type; nested pairs are removed from the
 * inside out. A parenthesis without a matching closing one is preserved.
 *
 * <p>Cleaning is done line by line. When a line changes, the spaces left over at
 * the ends are discarded (a residual space would shift the ASS centering, because
 * the space has width in the character grid) and lines that became blank do not
 * enter the result. If all the text is consumed, the result is an empty string —
 * it is up to the caller to decide whether the entry should be discarded.
 */
public final class ParenthesisStripper {

    /** Full-width pair: {@code （…）}, without inner parentheses of any kind. */
    private static final Pattern FULL_WIDTH = Pattern.compile("（[^（）()]*）");

    /** Half-width pair: {@code (…)}, without inner parentheses of any kind. */
    private static final Pattern HALF_WIDTH = Pattern.compile("\\([^（）()]*\\)");

    private ParenthesisStripper() {
        // Utility class: must not be instantiated.
    }

    /**
     * Removes the passages between round parentheses and returns the cleaned text.
     *
     * @param text text of an entry (may contain line breaks), or {@code null}
     * @return the text without the passages between parentheses; empty if everything is removed
     */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String cleaned = stripLine(line);
            if (!cleaned.equals(line)) {
                cleaned = cleaned.strip();
            }
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Removes the pairs from a single line, repeating until there is no further change.
     *
     * <p>The repetition is necessary for nested pairs: the inner pair only becomes
     * visible (and removable) after the ones inside have already been removed.
     *
     * @param line line to clean
     * @return the line without the passages between parentheses
     */
    private static String stripLine(String line) {
        if (line.isEmpty()) {
            return line;
        }
        String current = line;
        String previous;
        do {
            previous = current;
            current = FULL_WIDTH.matcher(current).replaceAll("");
            current = HALF_WIDTH.matcher(current).replaceAll("");
        } while (!current.equals(previous));
        return current;
    }
}
