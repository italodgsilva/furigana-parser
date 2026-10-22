package com.shinji2324.furigana.reading;

import com.shinji2324.furigana.japanese.JapaneseScript;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts explicit reading hints written as {@code 漢字(よみ)} or
 * {@code 漢字（よみ）} from SRT text. These hints take highest priority and
 * have their parentheses removed from the processed text.
 */
public final class SrtReadingHintExtractor {

    private static final Pattern HINT_HALF = Pattern.compile("([^\\s()（）]+)\\(([^()（）]+)\\)");
    private static final Pattern HINT_FULL = Pattern.compile("([^\\s()（）]+)（([^（）()]+)）");

    private SrtReadingHintExtractor() {
    }

    /**
     * Extracts reading hints from the given text.
     *
     * @param text source text (typically the original SRT content or an entry text)
     * @return map of base surface to normalized reading (hiragana)
     */
    public static Map<String, String> extractHints(String text) {
        Map<String, String> hints = new LinkedHashMap<>();
        if (text == null) {
            return hints;
        }
        extractFromPattern(text, HINT_HALF, hints);
        extractFromPattern(text, HINT_FULL, hints);
        return hints;
    }

    private static void extractFromPattern(String text, Pattern pattern, Map<String, String> hints) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String base = matcher.group(1);
            String reading = matcher.group(2);
            if (base == null || reading == null) {
                continue;
            }
            base = base.trim();
            reading = reading.trim();
            if (base.isEmpty() || reading.isEmpty()) {
                continue;
            }
            if (!JapaneseScript.containsKanji(base)) {
                continue;
            }
            if (!isHintReading(reading)) {
                continue;
            }
            String normalized = normalizeReading(reading);
            if (normalized == null || normalized.isBlank()) {
                continue;
            }
            // Keep first occurrence to be deterministic
            hints.putIfAbsent(base, normalized);
        }
    }

    private static boolean containsKatakana(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u30A0' && c <= '\u30FF') {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllKatakana(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u30A0' && c <= '\u30FF') {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isHintReading(String reading) {
        if (reading == null) {
            return false;
        }
        for (int i = 0; i < reading.length(); i++) {
            char c = reading.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c >= '\u3041' && c <= '\u3096') {
                continue;
            }
            if (c >= '\u30A1' && c <= '\u30FF') {
                continue;
            }
            if (c == '\u30FB') {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Removes hint parentheses from the text, replacing {@code 漢字(よみ)} with
     * {@code 漢字}. Only hints that contain kanji in the base are removed.
     */
    public static String removeHintParentheses(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        result = removeHintPattern(result, HINT_HALF);
        result = removeHintPattern(result, HINT_FULL);
        return result;
    }

    private static String removeHintPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (matcher.find()) {
            String base = matcher.group(1);
            String reading = matcher.group(2);
            if (base == null || reading == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            base = base.trim();
            reading = reading.trim();
            if (base.isEmpty() || reading.isEmpty()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            if (!JapaneseScript.containsKanji(base)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            if (!isHintReading(reading)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(base));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


    private static String normalizeReading(String reading) {
        if (reading == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(reading.length() * 2);
        for (int i = 0; i < reading.length(); i++) {
            char c = reading.charAt(i);
            // Convert katakana to hiragana
            if (c >= '\u30A1' && c <= '\u30F6') {
                sb.append((char) (c - 0x60));
                continue;
            }
            if (c == '\u30F4') { // ヴ
                sb.append('\u3094'); // ゔ
                continue;
            }
            if (c == '\u30F7') {
                sb.append('\u3095');
                continue;
            }
            if (c == '\u30F8') {
                sb.append('\u3096');
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
