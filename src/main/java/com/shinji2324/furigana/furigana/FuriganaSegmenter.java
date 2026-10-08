package com.shinji2324.furigana.furigana;

import com.shinji2324.furigana.japanese.JapaneseScript;

import java.util.ArrayList;
import java.util.List;

/**
 * Separa a superfície de um token em segmentos de furigana.
 *
 * <p>Um token morfológico pode misturar kanji e okurigana (ex.: {@code 勉強し} com
 * leitura {@code べんきょうし}). Apenas o trecho de kanji deve receber furigana;
 * o okurigana é preservado como texto sem leitura. Esta classe alinha a leitura
 * (em hiragana) à superfície e produz a sequência de {@link FuriganaSegment}s.
 *
 * <p>Regras:
 * <ul>
 *   <li>sem kanji na superfície ou sem leitura → um único segmento sem furigana;</li>
 *   <li>bloco de kanji seguido de okurigana → a leitura vai até onde o okurigana
 *       começa na leitura (ex.: {@code 食べる}/{@code たべる} → {@code 食=た}, {@code べる=∅});</li>
 *   <li>kanji isolado ou no fim do token → recebe o restante da leitura
 *       (ex.: {@code 日本語}/{@code にほんご} → {@code 日本語=にほんご}).</li>
 * </ul>
 *
 * <p>Quando a leitura não pode ser alinhada (leitura irregular), o restante da
 * leitura é atribuído ao bloco de kanji, evitando perder texto.
 */
public final class FuriganaSegmenter {

    /**
     * Segmenta um token em trechos com e sem furigana.
     *
     * @param surface superfície do token (texto original)
     * @param reading leitura em hiragana, ou {@code null}
     * @return segmentos na ordem em que aparecem; nunca {@code null}
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
                    int position = indexOfEquivalent(reading, okurigana, j);
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
     * Procura a primeira posição a partir de {@code from} em que {@code target}
     * ocorre na leitura, comparando kana com equivalência hiragana/katakana.
     *
     * @return índice inicial da ocorrência, ou {@code -1}
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
     * Compara dois caracteres tratando katakana e hiragana como equivalentes.
     */
    private static boolean equivalent(char first, char second) {
        if (first == second) {
            return true;
        }
        return JapaneseScript.toHiragana(String.valueOf(first))
                .equals(JapaneseScript.toHiragana(String.valueOf(second)));
    }
}
