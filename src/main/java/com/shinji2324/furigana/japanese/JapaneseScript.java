package com.shinji2324.furigana.japanese;

import java.util.regex.Pattern;

/**
 * Utilities for classifying Japanese script.
 *
 * <p>Centralizes the character-type detection used by the tokenizers and by the
 * pipeline, avoiding duplicating regular expressions. It is part of the
 * {@code japanese} layer and knows nothing about readings, furigana, or output
 * formats.
 */
public final class JapaneseScript {

    /** Pattern: one or more Han ideograms (kanji), including extensions. */
    private static final Pattern KANJI = Pattern.compile("[\\p{IsHan}]+");

    /** One or more hiragana characters. */
    private static final Pattern HIRAGANA = Pattern.compile("[\\p{IsHiragana}]+");

    /** One or more katakana characters. */
    private static final Pattern KATAKANA = Pattern.compile("[\\p{IsKatakana}]+");

    /** One or more Unicode punctuation characters (includes 。、？！・). */
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{P}]+");

    /** One or more whitespace characters. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private JapaneseScript() {
        // Utility class: must not be instantiated.
    }

    /**
     * Tells whether the text contains at least one Han ideogram (kanji).
     *
     * @param text text to inspect (may be {@code null})
     * @return {@code true} if there is kanji
     */
    public static boolean containsKanji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return KANJI.matcher(text).find();
    }

    /**
     * Tells whether the character is a Han ideogram (kanji).
     *
     * @param c character to inspect
     * @return {@code true} if it is kanji
     */
    public static boolean isKanji(char c) {
        return KANJI.matcher(String.valueOf(c)).matches();
    }

    /**
     * Tells whether the character is a kana (hiragana or katakana).
     *
     * @param c character to inspect
     * @return {@code true} if it is kana
     */
    public static boolean isKana(char c) {
        return HIRAGANA.matcher(String.valueOf(c)).matches()
                || KATAKANA.matcher(String.valueOf(c)).matches();
    }

    /**
     * Converts katakana characters to hiragana, preserving the rest.
     *
     * <p>The conversion shifts the katakana of the main block (U+30A1–U+30F6) by
     * 0x60 positions; characters already in hiragana, punctuation, and the
     * prolonged sound mark {@code ー} remain unchanged.
     *
     * @param text text to normalize (may be {@code null})
     * @return text with katakana converted to hiragana
     */
    public static String toHiragana(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u30A1' && c <= '\u30F6') {
                builder.append((char) (c - 0x60));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * Classifies the text according to the type of script it is composed of.
     *
     * <p>The text is classified only when it is composed of a single type of
     * script; combinations (e.g. {@code 勉強し}) result in {@code UNKNOWN}.
     *
     * @param text text to classify
     * @return {@code KANJI}, {@code HIRAGANA}, {@code KATAKANA},
     *         {@code PUNCTUATION}, {@code WHITESPACE} or {@code UNKNOWN}
     */
    public static String classify(String text) {
        if (text == null || text.isEmpty()) {
            return "UNKNOWN";
        }
        if (KANJI.matcher(text).matches()) {
            return "KANJI";
        }
        if (HIRAGANA.matcher(text).matches()) {
            return "HIRAGANA";
        }
        if (KATAKANA.matcher(text).matches()) {
            return "KATAKANA";
        }
        if (PUNCTUATION.matcher(text).matches()) {
            return "PUNCTUATION";
        }
        if (WHITESPACE.matcher(text).matches()) {
            return "WHITESPACE";
        }
        return "UNKNOWN";
    }
}
