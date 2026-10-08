package com.shinji2324.furigana.japanese;

import com.atilika.kuromoji.ipadic.Tokenizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizador morfológico baseado no Kuromoji (dicionário IPADIC).
 *
 * <p>Diferente do {@link ScriptJapaneseTokenizer}, faz análise morfológica real:
 * reconhece verbos conjugados, partículas e okurigana, e fornece a leitura de
 * cada token. As leituras do Kuromoji vêm em katakana e são normalizadas para
 * hiragana.
 *
 * <p>A leitura fica {@code null} quando o dicionário não a conhece (o Kuromoji
 * usa {@code "*"} para símbolos, números e latim). Cada token também carrega os
 * índices inicial/final no texto tokenizado, preservados para o posicionamento
 * posterior do furigana.
 *
 * <p>A classe é um adaptador fino: toda a segmentação é delegada ao Kuromoji e a
 * camada {@code japanese} continua sem conhecer furigana ou formatos de saída.
 */
public class KuromojiTokenizer implements JapaneseTokenizer {

    /** Dicionário IPADIC com que o tokenizador é construído por padrão. */
    private final Tokenizer tokenizer;

    /** Cria o tokenizador com o dicionário IPADIC padrão. */
    public KuromojiTokenizer() {
        this(new Tokenizer());
    }

    /**
     * Cria o tokenizador com uma instância de Kuromoji já configurada.
     *
     * @param tokenizer tokenizador do Kuromoji a ser adaptado
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
     * Normaliza a leitura do Kuromoji: descarta o marcador {@code "*"} (leitura
     * desconhecida) e converte o katakana para hiragana.
     *
     * @param reading leitura crua do Kuromoji
     * @return leitura em hiragana, ou {@code null} quando desconhecida
     */
    private String normalizeReading(String reading) {
        if (reading == null || reading.isBlank() || "*".equals(reading)) {
            return null;
        }
        return JapaneseScript.toHiragana(reading);
    }
}
