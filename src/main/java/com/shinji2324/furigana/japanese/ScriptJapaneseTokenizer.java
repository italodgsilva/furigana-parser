package com.shinji2324.furigana.japanese;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provisional tokenizer based on script patterns.
 *
 * <p>Segments the text into consecutive runs of the same character type (kanji,
 * hiragana, katakana, punctuation, Latin, etc.), so that the sentence
 * "今日は日本語を勉強します。" produces, for example:
 *
 * <pre>
 * 今日 | は | 日本語 | を | 勉強 | します | 。
 * </pre>
 *
 * <p>It is a provisional implementation, kept as a lightweight/offline
 * alternative to {@link KuromojiTokenizer}. Since it does not perform
 * morphological analysis, it produces no readings (all tokens have
 * {@code reading == null}) and does not split okurigana within a single token.
 *
 * <p>Ensures that no non-whitespace character is discarded — unrecognized
 * characters are preserved as tokens of type {@code UNKNOWN}. Whitespace is
 * ignored (does not generate tokens).
 */
public class ScriptJapaneseTokenizer implements JapaneseTokenizer {

    /**
     * Segmentation pattern: a run of a single type of Japanese character, a
     * Latin alphanumeric group, a run of whitespace (discarded) or, as a last
     * resort, any isolated character (preserved as {@code UNKNOWN}).
     *
     * <p>{@code \p{P}} matches Unicode punctuation (includes Japanese
     * punctuation such as 。、？！・), unlike {@code \p{Punct}}, which is
     * limited to ASCII.
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[\\p{IsHan}]+"
                    + "|[\\p{IsHiragana}]+"
                    + "|[\\p{IsKatakana}]+"
                    + "|[\\p{P}]+"
                    + "|[a-zA-Z0-9]+"
                    + "|[^\\s]");

    @Override
    public List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            tokens.add(new Token(value, JapaneseScript.classify(value), null, matcher.start(), matcher.end()));
        }

        return tokens;
    }
}
