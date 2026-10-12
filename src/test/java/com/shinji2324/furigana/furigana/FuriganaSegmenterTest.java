package com.shinji2324.furigana.furigana;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link FuriganaSegmenter} (separation of kanji and okurigana).
 */
class FuriganaSegmenterTest {

    private final FuriganaSegmenter segmenter = new FuriganaSegmenter();

    @Test
    @DisplayName("Kanji-only word receives the whole reading")
    void kanjiOnlyGetsWholeReading() {
        assertEquals(List.of(new FuriganaSegment("日本語", "にほんご")),
                segmenter.segment("日本語", "にほんご"));
    }

    @Test
    @DisplayName("Separates kanji from trailing okurigana")
    void splitsTrailingOkurigana() {
        assertEquals(List.of(new FuriganaSegment("勉強", "べんきょう"), new FuriganaSegment("し", null)),
                segmenter.segment("勉強し", "べんきょうし"));
    }

    @Test
    @DisplayName("Separates verb okurigana (食べる)")
    void splitsVerbOkurigana() {
        assertEquals(List.of(new FuriganaSegment("食", "た"), new FuriganaSegment("べる", null)),
                segmenter.segment("食べる", "たべる"));
    }

    @Test
    @DisplayName("Separates leading kana (お茶)")
    void splitsLeadingKana() {
        assertEquals(List.of(new FuriganaSegment("お", null), new FuriganaSegment("茶", "ちゃ")),
                segmenter.segment("お茶", "おちゃ"));
    }

    @Test
    @DisplayName("Separates kanji at the start and end (振り仮名)")
    void splitsKanjiBeforeAndAfterKana() {
        assertEquals(List.of(new FuriganaSegment("振", "ふ"),
                        new FuriganaSegment("り", null),
                        new FuriganaSegment("仮名", "がな")),
                segmenter.segment("振り仮名", "ふりがな"));
    }

    @Test
    @DisplayName("Separates internal okurigana (気持ち)")
    void splitsInternalOkurigana() {
        assertEquals(List.of(new FuriganaSegment("気持", "きも"), new FuriganaSegment("ち", null)),
                segmenter.segment("気持ち", "きもち"));
    }

    @Test
    @DisplayName("Trailing okurigana matches the end of the reading (大嫌い)")
    void trailingOkuriganaMatchesReadingSuffix() {
        // The trailing "い" cannot match the "い" in "だい": the kanji reading
        // is "だいきら" and the final "い" is okurigana.
        assertEquals(List.of(new FuriganaSegment("大嫌", "だいきら"), new FuriganaSegment("い", null)),
                segmenter.segment("大嫌い", "だいきらい"));
    }

    @Test
    @DisplayName("Long trailing okurigana also matches the end of the reading (大きい)")
    void longTrailingOkuriganaMatchesReadingSuffix() {
        assertEquals(List.of(new FuriganaSegment("大", "おお"), new FuriganaSegment("きい", null)),
                segmenter.segment("大きい", "おおきい"));
    }

    @Test
    @DisplayName("Normalizes a katakana reading to hiragana")
    void normalizesKatakanaReading() {
        assertEquals(List.of(new FuriganaSegment("日本語", "にほんご")),
                segmenter.segment("日本語", "ニホンゴ"));
    }

    @Test
    @DisplayName("Text without kanji becomes a single segment without a reading")
    void textWithoutKanjiHasNoReading() {
        assertEquals(List.of(new FuriganaSegment("ありがとう", null)),
                segmenter.segment("ありがとう", "ありがとう"));
    }

    @Test
    @DisplayName("Kanji without a reading becomes a segment without furigana")
    void kanjiWithoutReadingHasNoFurigana() {
        assertEquals(List.of(new FuriganaSegment("漢字", null)),
                segmenter.segment("漢字", null));
    }

    @Test
    @DisplayName("Concatenating the segments reconstructs the surface")
    void segmentsReconstructSurface() {
        List<String[]> samples = List.of(
                new String[]{"日本語", "にほんご"},
                new String[]{"勉強し", "べんきょうし"},
                new String[]{"食べる", "たべる"},
                new String[]{"お茶", "おちゃ"},
                new String[]{"振り仮名", "ふりがな"});

        for (String[] sample : samples) {
            String reconstructed = segmenter.segment(sample[0], sample[1]).stream()
                    .map(FuriganaSegment::text)
                    .reduce("", String::concat);
            assertEquals(sample[0], reconstructed, "reconstructed surface of " + sample[0]);
        }
    }

    @Test
    @DisplayName("Empty or null surface yields an empty list")
    void emptySurfaceYieldsNoSegments() {
        assertEquals(List.of(), segmenter.segment(null, "にほんご"));
        assertEquals(List.of(), segmenter.segment("", "にほんご"));
    }
}
