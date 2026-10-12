package com.shinji2324.furigana.japanese;

import com.atilika.kuromoji.ipadic.Tokenizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Morphological tokenizer based on Kuromoji (IPADIC dictionary).
 *
 * <p>Unlike {@link ScriptJapaneseTokenizer}, it performs real morphological
 * analysis: it recognizes conjugated verbs, particles, and okurigana, and
 * provides the reading of each token. Kuromoji's readings come in katakana and
 * are normalized to hiragana.
 *
 * <p>The reading is {@code null} when the dictionary does not know it (Kuromoji
 * uses {@code "*"} for symbols, numbers, and Latin). Each token also carries the
 * start/end indices in the tokenized text, preserved for the later positioning
 * of the furigana.
 *
 * <p>The class is a thin adapter: all segmentation is delegated to Kuromoji and
 * the {@code japanese} layer still knows nothing about furigana or output
 * formats.
 */
public class KuromojiTokenizer implements JapaneseTokenizer {

    /** IPADIC dictionary with which the tokenizer is built by default. */
    private final Tokenizer tokenizer;

    /** Creates the tokenizer with the default IPADIC dictionary. */
    public KuromojiTokenizer() {
        this(new Tokenizer());
    }

    /**
     * Creates the tokenizer with an already configured Kuromoji instance.
     *
     * @param tokenizer Kuromoji tokenizer to be adapted
     */
    public KuromojiTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        for (com.atilika.kuromoji.ipadic.Token kuromojiToken : tokenizer.tokenize(text)) {
            String surface = kuromojiToken.getSurface();
            String reading = normalizeReading(kuromojiToken.getReading());
            int start = kuromojiToken.getPosition();
            tokens.add(new Token(
                    surface,
                    kuromojiToken.getPartOfSpeechLevel1(),
                    reading,
                    start,
                    start + surface.length()));
        }
        return tokens;
    }

    /**
     * Normalizes the Kuromoji reading: discards the {@code "*"} marker (unknown
     * reading) and converts the katakana to hiragana.
     *
     * @param reading raw Kuromoji reading
     * @return reading in hiragana, or {@code null} when unknown
     */
    private String normalizeReading(String reading) {
        if (reading == null || reading.isBlank() || "*".equals(reading)) {
            return null;
        }
        return JapaneseScript.toHiragana(reading);
    }
}
