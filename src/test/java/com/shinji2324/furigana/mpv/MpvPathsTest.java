package com.shinji2324.furigana.mpv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link MpvPaths}.
 */
class MpvPathsTest {

    @Test
    @DisplayName("Generates the ASS next to the SRT, swapping the extension")
    void derivesSiblingPath() {
        assertEquals(Path.of("/tmp/show.furigana.ass"),
                MpvPaths.furiganaSubtitle(Path.of("/tmp/show.srt")));
    }

    @Test
    @DisplayName("Preserves intermediate name suffixes")
    void keepsIntermediateSuffixes() {
        assertEquals(Path.of("fallback.ja.furigana.ass"),
                MpvPaths.furiganaSubtitle(Path.of("fallback.ja.srt")));
    }

    @Test
    @DisplayName("Accepts a file without extension")
    void handlesFileWithoutExtension() {
        assertEquals(Path.of("no-extension.furigana.ass"),
                MpvPaths.furiganaSubtitle(Path.of("no-extension")));
    }
}
