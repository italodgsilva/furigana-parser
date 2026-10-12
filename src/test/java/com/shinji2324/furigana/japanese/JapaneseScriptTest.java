package com.shinji2324.furigana.japanese;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JapaneseScript}.
 */
class JapaneseScriptTest {

    @Test
    @DisplayName("Detects the presence of kanji")
    void detectsKanji() {
        assertTrue(JapaneseScript.containsKanji("日本語"));
        assertTrue(JapaneseScript.containsKanji("食べる"));
        assertFalse(JapaneseScript.containsKanji("ひらがなカタカナ。"));
        assertFalse(JapaneseScript.containsKanji(null));
        assertFalse(JapaneseScript.containsKanji(""));
    }

    @Test
    @DisplayName("Identifies individual characters")
    void identifiesIndividualCharacters() {
        assertTrue(JapaneseScript.isKanji('漢'));
        assertFalse(JapaneseScript.isKanji('か'));

        assertTrue(JapaneseScript.isKana('か'));
        assertTrue(JapaneseScript.isKana('カ'));
        assertFalse(JapaneseScript.isKana('漢'));
    }

    @Test
    @DisplayName("Converts katakana to hiragana preserving the rest")
    void convertsKatakanaToHiragana() {
        assertEquals("かたかな", JapaneseScript.toHiragana("カタカナ"));
        assertEquals("ひらがな", JapaneseScript.toHiragana("ひらがな"));
        assertEquals("日本語とabc", JapaneseScript.toHiragana("日本語とabc"));
        assertEquals("こーひー", JapaneseScript.toHiragana("コーヒー"));
        assertNull(JapaneseScript.toHiragana(null));
    }

    @Test
    @DisplayName("Classifies the text by script type")
    void classifiesByScript() {
        assertEquals("KANJI", JapaneseScript.classify("日本語"));
        assertEquals("HIRAGANA", JapaneseScript.classify("ひらがな"));
        assertEquals("KATAKANA", JapaneseScript.classify("カタカナ"));
        assertEquals("PUNCTUATION", JapaneseScript.classify("。"));
        assertEquals("UNKNOWN", JapaneseScript.classify("勉強し"));
        assertEquals("UNKNOWN", JapaneseScript.classify(null));
    }
}
