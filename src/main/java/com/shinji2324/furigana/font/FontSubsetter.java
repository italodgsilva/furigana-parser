package com.shinji2324.furigana.font;

import java.util.Set;

/**
 * Reduz uma fonte aos glifos necessários para um conjunto de code points.
 *
 * <p>O subsetting é uma etapa opcional entre localizar a fonte ({@link
 * FontLocator}) e embutí-la no ASS ({@code AssFontEmbedder}). A embedação não
 * precisa conhecer o algoritmo: basta receber os bytes já reduzidos. A
 * implementação disponível usa o HarfBuzz ({@link HarfBuzzSubsetter}) e o
 * pipeline recai para a fonte completa quando ela não está disponível.
 */
public interface FontSubsetter {

    /**
     * Reduz a fonte informada aos glifos usados pelos code points.
     *
     * @param data       bytes de uma única face sfnt ({@code .ttf}/{@code .otf})
     * @param codePoints code points Unicode a manter (texto e leituras)
     * @return os bytes da fonte reduzida, ou {@code null} quando não foi possível
     *         reduzir — nesse caso a fonte original deve ser usada
     */
    byte[] subset(byte[] data, Set<Integer> codePoints);
}
