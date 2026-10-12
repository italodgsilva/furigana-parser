package com.shinji2324.furigana.furigana;

import com.shinji2324.furigana.japanese.JapaneseScript;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a token's surface into furigana segments.
 *
 * <p>A morphological token can mix kanji and okurigana (e.g. {@code 勉強し} with
 * reading {@code べんきょうし}). Only the kanji portion should receive furigana;
 * the okurigana is preserved as text without a reading. This class aligns the
 * reading (in hiragana) with the surface and produces the sequence of
 * {@link FuriganaSegment}s.
 *
 * <p>Rules:
 * <ul>
 *   <li>no kanji in the surface or no reading → a single segment without furigana;</li>
 *   <li>kanji block followed by okurigana → the reading goes up to where the
 *       okurigana starts in the reading (e.g. {@code 食べる}/{@code たべる} →
 *       {@code 食=た}, {@code べる=∅});</li>
 *   <li>isolated kanji or kanji at the end of the token → receives the rest of
 *       the reading (e.g. {@code 日本語}/{@code にほんご} → {@code 日本語=にほんご}).</li>
 * </ul>
 *
 * <p>When the reading cannot be aligned (irregular reading), the rest of the
 * reading is assigned to the kanji block, avoiding losing text.
 */
public final class FuriganaSegmenter {

    /**
     * Segments a token into excerpts with and without furigana.
     *
     * @param surface surface of the token (original text)
     * @param reading reading in hiragana, or {@code null}
     * @return segments in the order they appear; never {@code null}
     */
    public List<FuriganaSegment> segment(String surface, String reading) {
        List<FuriganaSegment> segments = new ArrayList<>();
        if (surface == null || surface.isEmpty()) {
            return segments;
        }
        if (reading == null || reading.isEmpty() || !JapaneseScript.containsKanji(surface)) {
            segments.add(new FuriganaSegment(surface, null));
            return segments;
        }
        reading = JapaneseScript.toHiragana(reading);

        int surfaceLength = surface.length();
        int readingLength = reading.length();
        int i = 0;
        int j = 0;
        StringBuilder kana = new StringBuilder();

        while (i < surfaceLength) {
            char current = surface.charAt(i);
            if (JapaneseScript.isKanji(current)) {
                if (kana.length() > 0) {
                    segments.add(new FuriganaSegment(kana.toString(), null));
                    kana.setLength(0);
                }

                int blockStart = i;
                while (i < surfaceLength && JapaneseScript.isKanji(surface.charAt(i))) {
                    i++;
                }
                String kanjiBlock = surface.substring(blockStart, i);

                int okuriganaEnd = i;
                while (okuriganaEnd < surfaceLength && !JapaneseScript.isKanji(surface.charAt(okuriganaEnd))) {
                    okuriganaEnd++;
                }
                String okurigana = surface.substring(i, okuriganaEnd);

                String blockReading;
                if (okurigana.isEmpty()) {
                    blockReading = reading.substring(Math.min(j, readingLength));
                    j = readingLength;
                } else {
                    int position = okuriganaPosition(reading, okurigana, j, okuriganaEnd == surfaceLength);
                    if (position >= 0) {
                        blockReading = reading.substring(j, position);
                        j = position;
                    } else {
                        blockReading = reading.substring(Math.min(j, readingLength));
                        j = readingLength;
                    }
                }

                segments.add(new FuriganaSegment(kanjiBlock, blockReading.isEmpty() ? null : blockReading));
            } else {
                if (j < readingLength && equivalent(current, reading.charAt(j))) {
                    j++;
                }
                kana.append(current);
                i++;
            }
        }

        if (kana.length() > 0) {
            segments.add(new FuriganaSegment(kana.toString(), null));
        }
        return segments;
    }

    /**
     * Determines where the okurigana starts in the reading.
     *
     * <p>When the okurigana ends the token, it must match the <em>end</em> of the
     * reading — the last excerpt of the text corresponds to the last excerpt of
     * the reading. This avoids confusing the kana of the okurigana with an equal
     * kana inside the kanji reading: in {@code 大嫌い}/{@code だいきらい}, the
     * final {@code い} cannot match the {@code い} of {@code だい}.
     *
     * <p>In the other cases, it looks for the first occurrence starting at
     * {@code from}.
     *
     * @param reading   reading in hiragana
     * @param okurigana kana excerpt that follows the kanji block
     * @param from      position from which to search
     * @param trailing  whether the okurigana ends the token
     * @return starting index of the okurigana in the reading, or {@code -1}
     */
    private static int okuriganaPosition(String reading, String okurigana, int from, boolean trailing) {
        if (trailing) {
            int suffix = reading.length() - okurigana.length();
            if (suffix >= from && matchesAt(reading, okurigana, suffix)) {
                return suffix;
            }
        }
        return indexOfEquivalent(reading, okurigana, from);
    }

    /**
     * Looks for the first position starting at {@code from} where {@code target}
     * occurs in the reading, comparing kana with hiragana/katakana equivalence.
     *
     * @return starting index of the occurrence, or {@code -1}
     */
    private static int indexOfEquivalent(String reading, String target, int from) {
        int lastStart = reading.length() - target.length();
        for (int start = Math.max(from, 0); start <= lastStart; start++) {
            if (matchesAt(reading, target, start)) {
                return start;
            }
        }
        return -1;
    }

    private static boolean matchesAt(String reading, String target, int start) {
        for (int offset = 0; offset < target.length(); offset++) {
            if (!equivalent(target.charAt(offset), reading.charAt(start + offset))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two characters treating katakana and hiragana as equivalent.
     */
    private static boolean equivalent(char first, char second) {
        if (first == second) {
            return true;
        }
        return JapaneseScript.toHiragana(String.valueOf(first))
                .equals(JapaneseScript.toHiragana(String.valueOf(second)));
    }
}
