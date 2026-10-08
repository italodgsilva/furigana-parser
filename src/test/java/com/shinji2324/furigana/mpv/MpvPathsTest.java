package com.shinji2324.furigana.mpv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes do {@link MpvPaths}.
 */
class MpvPathsTest {

    @Test
    @DisplayName("Gera o ASS ao lado da SRT, trocando a extensão")
    void derivesSiblingPath() {
        assertEquals(Path.of("/tmp/show.furigana.ass"),
                MpvPaths.furiganaSubtitle(Path.of("/tmp/show.srt")));
    }

    @Test
    @DisplayName("Preserva sufixos intermediários do nome")
    void keepsIntermediateSuffixes() {
        assertEquals(Path.of("fallback.ja.furigana.ass"),
                MpvPaths.furiganaSubtitle(Path.of("fallback.ja.srt")));
    }

    @Test
    @DisplayName("Aceita arquivo sem extensão")
    void handlesFileWithoutExtension() {
        assertEquals(Path.of("sem-extensao.furigana.ass"),
                MpvPaths.furiganaSubtitle(Path.of("sem-extensao")));
    }
}
