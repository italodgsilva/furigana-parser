package com.shinji2324.furigana.furigana;

import java.util.Set;
import java.util.TreeSet;

/**
 * Reúne os code points Unicode que aparecem na legenda processada.
 *
 * <p>É a entrada do subsetting: a fonte embutida precisa apenas dos glifos que a
 * legenda realmente desenha. São considerados o texto principal de cada entrada
 * (o mesmo usado nos eventos do estilo {@code Main}) e as leituras dos segmentos
 * (o texto dos eventos do estilo {@code Furigana}). Os override tags
 * ({@code \pos} etc.) não são glifos e por isso não entram.
 *
 * <p>O conjunto é ordenado para que o resultado não dependa da ordem de iteração.
 */
public final class UsedCodePoints {

    private UsedCodePoints() {
    }

    /**
     * Coleta os code points usados pelo documento.
     *
     * @param document documento processado (pode ser {@code null})
     * @return conjunto ordenado de code points
     */
    public static Set<Integer> from(FuriganaDocument document) {
        Set<Integer> codePoints = new TreeSet<>();
        if (document == null) {
            return codePoints;
        }
        for (FuriganaEntry entry : document.getEntries()) {
            addCodePoints(codePoints, entry.text());
            for (FuriganaSegment segment : entry.segments()) {
                addCodePoints(codePoints, segment.reading());
            }
        }
        return codePoints;
    }

    private static void addCodePoints(Set<Integer> target, String text) {
        if (text == null) {
            return;
        }
        text.codePoints().forEach(target::add);
    }
}
