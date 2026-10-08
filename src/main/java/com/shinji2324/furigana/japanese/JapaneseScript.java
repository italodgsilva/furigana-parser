package com.shinji2324.furigana.japanese;

import java.util.regex.Pattern;

/**
 * Utilitários de classificação de escrita japonesa.
 *
 * <p>Centraliza a detecção do tipo de caractere usada pelos tokenizadores e pelo
 * pipeline, evitando duplicar expressões regulares. Faz parte da camada
 * {@code japanese} e não conhece leituras, furigana ou formatos de saída.
 */
public final class JapaneseScript {

    /** Partícula: um ou mais ideogramas Han (kanji), incluindo extensões. */
    private static final Pattern KANJI = Pattern.compile("[\\p{IsHan}]+");

    /** Um ou mais caracteres hiragana. */
    private static final Pattern HIRAGANA = Pattern.compile("[\\p{IsHiragana}]+");

    /** Um ou mais caracteres katakana. */
    private static final Pattern KATAKANA = Pattern.compile("[\\p{IsKatakana}]+");

    /** Um ou mais caracteres de pontuação Unicode (inclui 。、？！・). */
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{P}]+");

    /** Um ou mais espaços em branco. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private JapaneseScript() {
        // Classe utilitária: não deve ser instanciada.
    }

    /**
     * Informa se o texto contém ao menos um ideograma Han (kanji).
     *
     * @param text texto a inspecionar (pode ser {@code null})
     * @return {@code true} se houver kanji
     */
    public static boolean containsKanji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return KANJI.matcher(text).find();
    }

    /**
     * Informa se o caractere é um ideograma Han (kanji).
     *
     * @param c caractere a inspecionar
     * @return {@code true} se for kanji
     */
    public static boolean isKanji(char c) {
        return KANJI.matcher(String.valueOf(c)).matches();
    }

    /**
     * Informa se o caractere é um kana (hiragana ou katakana).
     *
     * @param c caractere a inspecionar
     * @return {@code true} se for kana
     */
    public static boolean isKana(char c) {
        return HIRAGANA.matcher(String.valueOf(c)).matches()
                || KATAKANA.matcher(String.valueOf(c)).matches();
    }

    /**
     * Converte caracteres katakana para hiragana, preservando os demais.
     *
     * <p>A conversão desloca os katakana do bloco principal (U+30A1–U+30F6) em
     * 0x60 posições; caracteres já em hiragana, pontuação e o alongador {@code ー}
     * permanecem inalterados.
     *
     * @param text texto a normalizar (pode ser {@code null})
     * @return texto com katakana convertido para hiragana
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
     * Classifica o texto conforme o tipo de escrita que o compõe.
     *
     * <p>O texto é classificado apenas quando composto por um único tipo de
     * escrita; combinações (ex.: {@code 勉強し}) resultam em {@code UNKNOWN}.
     *
     * @param text texto a classificar
     * @return {@code KANJI}, {@code HIRAGANA}, {@code KATAKANA},
     *         {@code PUNCTUATION}, {@code WHITESPACE} ou {@code UNKNOWN}
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
