package com.shinji2324.furigana.furigana;

import java.util.List;

/**
 * A processed subtitle entry, with furigana.
 *
 * <p>Preserves the identifier and the time range of the original SRT entry
 * (synchronization is not altered by processing) and keeps the full original
 * text, including internal line breaks.
 *
 * <p>The {@link FuriganaSegment}s represent the annotated segmentation of the
 * text. Concatenating the segments reconstructs the tokenized content; the
 * {@code text} field preserves the original text (with line breaks), which is
 * the source of truth for rendering.
 *
 * @param id       index of the entry in the subtitle (may be {@code null} when
 *                 the SRT has no numbering)
 * @param start    start timestamp, as written in the SRT (e.g. {@code 00:00:01,000})
 * @param end      end timestamp, as written in the SRT
 * @param text     original text of the entry, with line breaks preserved
 * @param segments list of annotated segments of the text
 */
public record FuriganaEntry(String id, String start, String end, String text, List<FuriganaSegment> segments) {

    /**
     * Compact constructor: copies the segment list to guarantee immutability.
     */
    public FuriganaEntry {
        segments = List.copyOf(segments);
    }
}