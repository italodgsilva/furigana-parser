package com.shinji2324.furigana.output;

/**
 * Geometria do furigana na tela, usada para posicionar cada leitura sobre o kanji.
 *
 * <p>Como o ASS não possui rubi nativo, o {@link AssGenerator} posiciona cada
 * leitura com a tag {@code \pos}, calculando a coordenada horizontal a partir de
 * uma grade de caracteres. A grade assume a aproximação usual das fontes CJK:
 * cada caractere de largura total (kanji, kana, pontuação japonesa) ocupa um
 * quadrado igual ao tamanho da fonte, e caracteres ASCII ocupam meio quadrado.
 * Isso mantém o alinhamento preciso para textos japoneses e aproximado para
 * trechos em latim.
 *
 * <p>As constantes pressupõem a resolução 1920x1080 declarada no
 * {@code /ass/default-style.ass}. Todas as coordenadas usam alinhamento
 * {@code \an5} (centro), de modo que o par {@code (x, y)} é o centro do texto.
 */
public final class AssLayout {

    /** Largura lógica da tela (deve casar com o PlayResX do template). */
    public static final int PLAY_RES_X = 1920;

    /** Altura lógica da tela (deve casar com o PlayResY do template). */
    public static final int PLAY_RES_Y = 1080;

    /** Tamanho da fonte do texto principal (deve casar com o estilo Main). */
    public static final int MAIN_FONT_SIZE = 64;

    /** Tamanho da fonte do furigana (deve casar com o estilo Furigana). */
    public static final int FURIGANA_FONT_SIZE = 32;

    /** Distância entre a base do texto principal e a base do furigana, em pixels. */
    public static final int FURIGANA_GAP = 12;

    /** Distância vertical entre linhas consecutivas do texto principal. */
    public static final int LINE_HEIGHT = 120;

    /** Margem inferior reservada para a última linha do texto principal. */
    public static final int MAIN_BOTTOM_MARGIN = 60;

    /** Largura de um caractere de largura total (um "quadrado" da grade). */
    public static final double CELL_WIDTH = MAIN_FONT_SIZE;

    private AssLayout() {
        // Classe utilitária: não deve ser instanciada.
    }

    /**
     * Coordenada vertical (centro) da linha principal.
     *
     * @param indexFromBottom distância da linha até a base do bloco (0 = última linha)
     * @return centro vertical da linha em pixels
     */
    public static double mainCenterY(int indexFromBottom) {
        double bottomLineCenter = PLAY_RES_Y - MAIN_BOTTOM_MARGIN - MAIN_FONT_SIZE / 2.0;
        return bottomLineCenter - indexFromBottom * LINE_HEIGHT;
    }

    /**
     * Coordenada vertical (centro) do furigana associado a uma linha principal.
     *
     * @param indexFromBottom distância da linha até a base do bloco (0 = última linha)
     * @return centro vertical do furigana em pixels
     */
    public static double furiganaCenterY(int indexFromBottom) {
        double offset = MAIN_FONT_SIZE / 2.0 + FURIGANA_GAP + FURIGANA_FONT_SIZE / 2.0;
        return mainCenterY(indexFromBottom) - offset;
    }

    /**
     * Calcula a coordenada horizontal (centro) de uma célula da grade.
     *
     * @param centerCell célula central do trecho, a partir do início da linha
     * @param totalCells largura total da linha, em células
     * @return centro horizontal em pixels, alinhado ao texto centralizado
     */
    public static double xForCenterCell(double centerCell, double totalCells) {
        double lineStart = (PLAY_RES_X - totalCells * CELL_WIDTH) / 2.0;
        return lineStart + centerCell * CELL_WIDTH;
    }

    /**
     * Soma a largura, em células, de um texto.
     *
     * @param text texto a medir (pode ser {@code null})
     * @return largura total em células
     */
    public static double cells(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += cellWidth(text.charAt(i));
        }
        return total;
    }

    /**
     * Largura de um caractere na grade: meia célula para ASCII/katakana
     * half-width, uma célula para os demais.
     *
     * @param c caractere a medir
     * @return {@code 0.5} ou {@code 1.0}
     */
    public static double cellWidth(char c) {
        if (c < '\u007F') {
            return 0.5;
        }
        if (c >= '\uFF61' && c <= '\uFF9F') {
            // Katakana half-width e pontuação half-width.
            return 0.5;
        }
        return 1.0;
    }
}
