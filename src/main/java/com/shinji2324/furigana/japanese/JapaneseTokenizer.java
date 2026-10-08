package com.shinji2324.furigana.japanese;

import java.util.List;

/**
 * Tokenizador de texto japonês.
 *
 * <p>Responsável por segmentar uma string em {@link Token}s, preservando a ordem
 * original. A camada {@code japanese} não conhece leituras, furigana ou formatos
 * de saída.
 *
 * <p>Esta é uma interface (a implementação concreta era uma decisão aberta no
 * architecture.md). A implementação atual é a {@link ScriptJapaneseTokenizer}
 * (segmentação por tipo de escrita, provisória); a implementação definitiva
 * utilizará o Kuromoji em uma etapa posterior.
 */
public interface JapaneseTokenizer {

    /**
     * Segmenta o texto informado em tokens.
     *
     * @param text texto a ser tokenizado
     * @return lista de tokens na ordem em que aparecem no texto
     */
    List<Token> tokenize(String text);
}