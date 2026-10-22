package com.shinji2324.furigana.reading;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SrtReadingHintExtractorTest {

    @Test
    void extractsHalfWidthHint() {
        String text = "五条悟(ごじょうさとる)は強い。";
        Map<String, String> hints = SrtReadingHintExtractor.extractHints(text);
        assertEquals("ごじょうさとる", hints.get("五条悟"));
    }

    @Test
    void extractsFullWidthHint() {
        String text = "虎杖悠仁（いたどりゆうじ）です。";
        Map<String, String> hints = SrtReadingHintExtractor.extractHints(text);
        assertEquals("いたどりゆうじ", hints.get("虎杖悠仁"));
    }

    @Test
    void normalizesKatakanaToHiragana() {
        String text = "学校(がっこう)";
        Map<String, String> hints = SrtReadingHintExtractor.extractHints(text);
        assertEquals("がっこう", hints.get("学校"));
    }

    @Test
    void ignoresHintsWithoutKanji() {
        String text = "テスト(てすと)と(笑)。";
        Map<String, String> hints = SrtReadingHintExtractor.extractHints(text);
        assertNull(hints.get("テスト"));
        assertNull(hints.get("笑"));
    }

    @Test
    void keepsFirstOccurrence() {
        String text = "五条悟(ごじょうさとる)と五条悟(ごじょうご)。";
        Map<String, String> hints = SrtReadingHintExtractor.extractHints(text);
        assertEquals("ごじょうさとる", hints.get("五条悟"));
    }

    @Test
    void removeHintParenthesesReplacesBase() {
        String text = "五条悟(ごじょうさとる)は強い。";
        String cleaned = SrtReadingHintExtractor.removeHintParentheses(text);
        assertEquals("五条悟は強い。", cleaned);
    }

    @Test
    void removeHintParenthesesKeepsOtherParens() {
        String text = "五条悟(ごじょうさとる)は強い(笑)。";
        String cleaned = SrtReadingHintExtractor.removeHintParentheses(text);
        assertEquals("五条悟は強い(笑)。", cleaned);
    }
}
