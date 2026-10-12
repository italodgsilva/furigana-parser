package com.shinji2324.furigana.furigana;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes do {@link FuriganaSegmenter} (separação de kanji e okurigana).
 */
class FuriganaSegmenterTest {

    private final FuriganaSegmenter segmenter = new FuriganaSegmenter();

    @Test
    @DisplayName("Palavra só de kanji recebe a leitura inteira")
    void kanjiOnlyGetsWholeReading() {
        assertEquals(List.of(new FuriganaSegment("日本語", "にほんご")),
                segmenter.segment("日本語", "にほんご"));
    }

    @Test
    @DisplayName("Separa kanji de okurigana final")
    void splitsTrailingOkurigana() {
        assertEquals(List.of(new FuriganaSegment("勉強", "べんきょう"), new FuriganaSegment("し", null)),
                segmenter.segment("勉強し", "べんきょうし"));
    }

    @Test
    @DisplayName("Separa okurigana de verbo (食べる)")
    void splitsVerbOkurigana() {
        assertEquals(List.of(new FuriganaSegment("食", "た"), new FuriganaSegment("べる", null)),
                segmenter.segment("食べる", "たべる"));
    }

    @Test
    @DisplayName("Separa kana inicial (お茶)")
    void splitsLeadingKana() {
        assertEquals(List.of(new FuriganaSegment("お", null), new FuriganaSegment("茶", "ちゃ")),
                segmenter.segment("お茶", "おちゃ"));
    }

    @Test
    @DisplayName("Separa kanji no início e no fim (振り仮名)")
    void splitsKanjiBeforeAndAfterKana() {
        assertEquals(List.of(new FuriganaSegment("振", "ふ"),
                        new FuriganaSegment("り", null),
                        new FuriganaSegment("仮名", "がな")),
                segmenter.segment("振り仮名", "ふりがな"));
    }

    @Test
    @DisplayName("Separa okurigana internal (気持ち)")
    void splitsInternalOkurigana() {
        assertEquals(List.of(new FuriganaSegment("気持", "きも"), new FuriganaSegment("ち", null)),
                segmenter.segment("気持ち", "きもち"));
    }

    @Test
    @DisplayName("Okurigana final casa com o fim da leitura (大嫌い)")
    void trailingOkuriganaMatchesReadingSuffix() {
        // O "い" do fim não pode casar com o "い" de "だい": a leitura do kanji
        // é "だいきら" e o "い" final é okurigana.
        assertEquals(List.of(new FuriganaSegment("大嫌", "だいきら"), new FuriganaSegment("い", null)),
                segmenter.segment("大嫌い", "だいきらい"));
    }

    @Test
    @DisplayName("Okurigana final longo também casa com o fim da leitura (大きい)")
    void longTrailingOkuriganaMatchesReadingSuffix() {
        assertEquals(List.of(new FuriganaSegment("大", "おお"), new FuriganaSegment("きい", null)),
                segmenter.segment("大きい", "おおきい"));
    }

    @Test
    @DisplayName("Normaliza leitura em katakana para hiragana")
    void normalizesKatakanaReading() {
        assertEquals(List.of(new FuriganaSegment("日本語", "にほんご")),
                segmenter.segment("日本語", "ニホンゴ"));
    }

    @Test
    @DisplayName("Texto sem kanji vira um único segmento sem leitura")
    void textWithoutKanjiHasNoReading() {
        assertEquals(List.of(new FuriganaSegment("ありがとう", null)),
                segmenter.segment("ありがとう", "ありがとう"));
    }

    @Test
    @DisplayName("Kanji sem leitura vira segmento sem furigana")
    void kanjiWithoutReadingHasNoFurigana() {
        assertEquals(List.of(new FuriganaSegment("漢字", null)),
                segmenter.segment("漢字", null));
    }

    @Test
    @DisplayName("A concatenação dos segmentos reconstrói a superfície")
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
            assertEquals(sample[0], reconstructed, "superfície recomposta de " + sample[0]);
        }
    }

    @Test
    @DisplayName("Superfície vazia ou nula gera lista vazia")
    void emptySurfaceYieldsNoSegments() {
        assertEquals(List.of(), segmenter.segment(null, "にほんご"));
        assertEquals(List.of(), segmenter.segment("", "にほんご"));
    }
}
