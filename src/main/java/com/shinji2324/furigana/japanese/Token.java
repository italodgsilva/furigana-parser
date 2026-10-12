package com.shinji2324.furigana.japanese;

/**
 * Unit identified during tokenization.
 *
 * <p>Besides the surface ({@code surface}) and the type ({@code kind}), a token
 * can carry the reading provided by the morphological tokenizer (Kuromoji),
 * always normalized to hiragana. The reading is {@code null} when the tokenizer
 * does not know it (symbols, numbers, Latin) or when the tokenizer is the
 * provisional {@link ScriptJapaneseTokenizer}, which produces no readings.
 *
 * <p>The offsets ({@code start}/{@code end}) are character indices in the
 * tokenized text and are {@code -1} when they do not apply. They are used by
 * later stages (e.g. positioning of the furigana in ASS).
 */
public class Token {

    /** Value used when the offset is not known. */
    public static final int UNKNOWN_OFFSET = -1;

    private final String surface;
    private final String kind;
    private final String reading;
    private final int start;
    private final int end;

    /**
     * Creates a token without a reading or offsets.
     *
     * @param surface surface of the token
     */
    public Token(String surface) {
        this(surface, "UNKNOWN");
    }

    /**
     * Creates a token without a reading or offsets.
     *
     * @param surface surface of the token
     * @param kind    classification of the token (depends on the tokenizer)
     */
    public Token(String surface, String kind) {
        this(surface, kind, null);
    }

    /**
     * Creates a token with a reading, but without known offsets.
     *
     * @param surface surface of the token
     * @param kind    classification of the token
     * @param reading reading in hiragana, or {@code null}
     */
    public Token(String surface, String kind, String reading) {
        this(surface, kind, reading, UNKNOWN_OFFSET, UNKNOWN_OFFSET);
    }

    /**
     * Creates a complete token.
     *
     * @param surface surface of the token
     * @param kind    classification of the token
     * @param reading reading in hiragana, or {@code null}
     * @param start   starting index in the tokenized text, or {@link #UNKNOWN_OFFSET}
     * @param end     end index (exclusive) in the tokenized text, or {@link #UNKNOWN_OFFSET}
     */
    public Token(String surface, String kind, String reading, int start, int end) {
        this.surface = surface;
        this.kind = kind;
        this.reading = reading;
        this.start = start;
        this.end = end;
    }

    public String getSurface() {
        return surface;
    }

    public String getKind() {
        return kind;
    }

    public String getReading() {
        return reading;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    /**
     * Tells whether the token has a non-empty reading.
     *
     * @return {@code true} when there is a reading
     */
    public boolean hasReading() {
        return reading != null && !reading.isBlank();
    }

    @Override
    public String toString() {
        return "Token{" +
                "surface='" + surface + '\'' +
                ", kind='" + kind + '\'' +
                ", reading='" + reading + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}
