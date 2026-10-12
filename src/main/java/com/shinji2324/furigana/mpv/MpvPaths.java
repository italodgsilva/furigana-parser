package com.shinji2324.furigana.mpv;

import java.nio.file.Path;

/**
 * File naming conventions used in the MPV integration.
 *
 * <p>The furigana subtitle is generated next to the source SRT, with the suffix
 * {@code .furigana.ass}. Examples:
 *
 * <pre>
 * episode.srt     → episode.furigana.ass
 * episode.ja.srt  → episode.ja.furigana.ass
 * no-extension    → no-extension.furigana.ass
 * </pre>
 */
public final class MpvPaths {

    /** Suffix and extension of the generated ASS file. */
    public static final String FURIGANA_SUFFIX = ".furigana.ass";

    private MpvPaths() {
        // Utility class: must not be instantiated.
    }

    /**
     * Derives the furigana subtitle path from the source SRT.
     *
     * <p>Removes the extension from the file name and appends
     * {@value #FURIGANA_SUFFIX}, keeping the same directory.
     *
     * @param srt path of the SRT subtitle
     * @return path of the ASS to be generated
     */
    public static Path furiganaSubtitle(Path srt) {
        String name = srt.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return srt.resolveSibling(base + FURIGANA_SUFFIX);
    }
}
