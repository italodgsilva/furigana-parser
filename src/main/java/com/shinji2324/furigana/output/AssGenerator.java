package com.shinji2324.furigana.output;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;

/**
 * Gerador provisório de saída ASS.
 *
 * <p>Esta implementação ainda é um esqueleto: cada segmento é impresso como
 * {@code texto -> leitura} dentro de uma unidade de diálogo fixa. A definição
 * final de posicionamento do furigana (estratégia de tags {@code \pos} do
 * ass-format.md) será implementada em uma etapa posterior, quando a composição
 * visual estiver planejada.
 *
 * <p>O gerador recebe um documento já processado e não consulta fontes de leitura.
 */
public class AssGenerator {

    /** Separador de linhas fixo, independente do sistema operacional. */
    private static final String LINE_SEPARATOR = "\n";

    /**
     * Gera um conteúdo ASS provisório a partir do documento.
     *
     * @param document documento processado
     * @return conteúdo ASS (ainda sem posicionamento de furigana)
     */
    public String generate(FuriganaDocument document) {
        if (document == null || document.getEntries().isEmpty()) {
            return new AssWriter().write("");
        }

        StringBuilder builder = new StringBuilder();
        for (FuriganaEntry entry : document.getEntries()) {
            for (FuriganaSegment segment : entry.segments()) {
                builder.append(segment.text())
                        .append(" -> ")
                        .append(segment.reading())
                        .append(LINE_SEPARATOR);
            }
        }

        return new AssWriter().write(builder.toString().stripTrailing());
    }
}