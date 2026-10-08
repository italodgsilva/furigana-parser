package com.shinji2324.furigana.japanese;

/**
 * Unidade identificada durante a tokenização.
 *
 * <p>Além da superfície ({@code surface}) e do tipo ({@code kind}), um token pode
 * carregar a leitura fornecida pelo tokenizador morfológico (Kuromoji), sempre
 * normalizada para hiragana. A leitura é {@code null} quando o tokenizador não a
 * conhece (símbolos, números, latim) ou quando o tokenizador é o provisório
 * {@link ScriptJapaneseTokenizer}, que não produz leituras.
 *
 * <p>Os deslocamentos ({@code start}/{@code end}) são índices de caractere no
 * texto tokenizado e valem {@code -1} quando não se aplicam. Eles são usados
 * pelas etapas posteriores (ex.: posicionamento do furigana no ASS).
 */
public class Token {

    /** Valor usado quando o deslocamento não é conhecido. */
    public static final int UNKNOWN_OFFSET = -1;

    private final String surface;
    private final String kind;
    private final String reading;
    private final int start;
    private final int end;

    /**
     * Cria um token sem leitura nem deslocamentos.
     *
     * @param surface superfície do token
     */
    public Token(String surface) {
        this(surface, "UNKNOWN");
    }

    /**
     * Cria um token sem leitura nem deslocamentos.
     *
     * @param surface superfície do token
     * @param kind    classificação do token (depende do tokenizador)
     */
    public Token(String surface, String kind) {
        this(surface, kind, null);
    }

    /**
     * Cria um token com leitura, mas sem deslocamentos conhecidos.
     *
     * @param surface superfície do token
     * @param kind    classificação do token
     * @param reading leitura em hiragana, ou {@code null}
     */
    public Token(String surface, String kind, String reading) {
        this(surface, kind, reading, UNKNOWN_OFFSET, UNKNOWN_OFFSET);
    }

    /**
     * Cria um token completo.
     *
     * @param surface superfície do token
     * @param kind    classificação do token
     * @param reading leitura em hiragana, ou {@code null}
     * @param start   índice inicial no texto tokenizado, ou {@link #UNKNOWN_OFFSET}
     * @param end     índice final (exclusivo) no texto tokenizado, ou {@link #UNKNOWN_OFFSET}
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
     * Informa se o token possui leitura não vazia.
     *
     * @return {@code true} quando há leitura
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
