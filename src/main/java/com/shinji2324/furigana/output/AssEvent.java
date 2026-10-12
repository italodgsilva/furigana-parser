package com.shinji2324.furigana.output;

/**
 * A dialogue event of an ASS file.
 *
 * <p>The {@code start} and {@code end} fields must already be in ASS format
 * ({@code H:MM:SS.CC}), usually obtained from {@link AssWriter#formatTime(String)}.
 *
 * @param layer display layer (0 is the default)
 * @param start start timestamp in ASS format
 * @param end   end timestamp in ASS format
 * @param style style name ({@code Main} or {@code Furigana})
 * @param text  event text, including the override tags ({@code \pos} etc.)
 */
public record AssEvent(int layer, String start, String end, String style, String text) {
}
