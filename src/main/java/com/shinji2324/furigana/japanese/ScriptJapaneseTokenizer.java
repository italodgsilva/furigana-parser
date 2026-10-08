package com.shinji2324.furigana.japanese;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizador provisório baseado em padrões de escrita (scripts).
 *
 * <p>Segmenta o texto por sequências consecutivas de um mesmo tipo de caractere
 * (kanji, hiragana, katakana, pontuação, latino etc.), de forma que a frase
 * "今日は日本語を勉強します。" produza, por exemplo:
 *
 * <pre>
 * 今日 | は | 日本語 | を | 勉強 | します | 。
 * </pre>
 *
 * <p>É uma implementação provisória que será substituída pelo Kuromoji em uma
 * etapa posterior, quando a segmentação morfológica (okurigana, verbos conjugados)
 * estiver disponível. Enquanto isso, serve como fallback e garante que nenhum
 * caractere seja descartado — caracteres não reconhecidos são preservados como
 * tokens de tipo {@code UNKNOWN}.
 *
 * <p>Espaços em branco são ignorados (não geram tokens).
 */
public class ScriptJapaneseTokenizer implements JapaneseTokenizer {

    /**
     * Padrão de segmentação: uma sequência de um único tipo de caractere japonês,
     * um grupo alfanumérico latino, uma sequência de espaços (descartada) ou, como
     * último recurso, um caractere isolado qualquer (preservado como {@code UNKNOWN}).
     *
     * <p>{@code \p{P}} corresponde à pontuação Unicode (inclui pontuação japonesa
     * como 。、？！・), ao contrário de {@code \p{Punct}}, que é limitada ao ASCII.
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
            tokens.add(new Token(value, detectKind(value)));
        }

        return tokens;
    }

    /**
     * Classifica um token conforme o tipo dominante de caractere que o compõe.
     *
     * @param text superfície do token (já pertence a um único tipo de escrita)
     * @return {@code KANJI}, {@code HIRAGANA}, {@code KATAKANA}, {@code PUNCTUATION},
     *         {@code WHITESPACE} ou {@code UNKNOWN}
     */
    private String detectKind(String text) {
        if (text.matches("[\\p{IsHan}]+")) {
            return "KANJI";
        }
        if (text.matches("[\\p{IsHiragana}]+")) {
            return "HIRAGANA";
        }
        if (text.matches("[\\p{IsKatakana}]+")) {
            return "KATAKANA";
        }
        if (text.matches("[\\p{P}]+")) {
            return "PUNCTUATION";
        }
        if (text.matches("\\s+")) {
            return "WHITESPACE";
        }
        return "UNKNOWN";
    }
}