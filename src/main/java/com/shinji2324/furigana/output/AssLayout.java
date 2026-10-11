package com.shinji2324.furigana.output;

/**
 * Geometria do furigana na tela, usada para posicionar cada leitura sobre o kanji.
 *
 * <p>Como o ASS não possui rubi nativo, o {@link AssGenerator} posiciona cada
 * leitura com a tag {@code \pos}, calculando a coordenada horizontal a partir de
 * uma grade de caracteres. Cada caractere de largura total (kanji, kana,
 * pontuação japonesa) ocupa um "em"; o katakana half-width ocupa meio "em"; e
 * os caracteres ASCII usam o avanço real da fonte do template (tabela
 * {@code ASCII_ADVANCES}). Assim o alinhamento fica preciso para o japonês e
 * para o latim presente nas legendas, enquanto a fonte do template for usada.
 *
 * <p><strong>O "em" não é o tamanho de fonte declarado no ASS.</strong> O libass
 * (motor de legendas do mpv) interpreta o {@code Fontsize} do ASS como a
 * <em>altura da linha</em> da fonte — o ascendente menos o descendente da tabela
 * {@code hhea} —, e não como o tamanho do "em". Ou seja, o avanço real de um
 * caractere de largura total é {@code Fontsize * EM_PER_FONT_SIZE}, com
 * {@code EM_PER_FONT_SIZE} dependente da fonte. Ignorar esse fator desloca
 * todas as leituras horizontalmente (o erro cresce com o comprimento da linha).
 * <p>As coordenadas verticais do texto principal usam a caixa de linha (o libass
 * a dimensiona exatamente para o {@code Fontsize}); já o furigana é posicionado
 * pelos "em" (as caixas de glifo), porque a caixa de linha desta fonte tem um
 * "leading" interno grande que deixaria a leitura distante do kanji.
 *
 * <p>As constantes pressupõem a resolução 1920x1080 e a fonte
 * {@code Noto Sans CJK JP}, ambos declarados no {@code /ass/default-style.ass}.
 * Todas as coordenadas usam alinhamento {@code \an5} (centro), de modo que o par
 * {@code (x, y)} é o centro do texto.
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

    /** Folga vertical entre o "em" do texto principal e o "em" do furigana, em pixels. */
    public static final int FURIGANA_GAP = -2;

    /** Distância vertical entre linhas consecutivas do texto principal. */
    public static final int LINE_HEIGHT = 120;

    /** Margem inferior reservada para a última linha do texto principal. */
    public static final int MAIN_BOTTOM_MARGIN = 60;

    /**
     * Fator que converte o tamanho de fonte declarado no ASS para a largura real
     * de um caractere de largura total (um "em"), conforme renderizado pelo
     * libass.
     *
     * <p>O libass dimensiona a fonte de modo que o {@code Fontsize} do ASS seja a
     * altura da linha ({@code hhea.ascender - hhea.descender}), e não o tamanho do
     * "em". Para a fonte do template ({@code Noto Sans CJK JP}):
     * {@code unitsPerEm = 1000}, {@code hhea.ascender = 1160} e
     * {@code hhea.descender = -288}, logo o avanço de um caractere de largura
     * total é {@code 1000 / (1160 + 288) = 1000/1448 ≈ 0,6906} do {@code Fontsize}.
     * Kanji e kana têm avanço de exatamente um "em".
     *
     * <p><strong>Atenção:</strong> este valor é específico da fonte do template.
     * Trocar a fonte exige recalcular o fator a partir das métricas {@code hhea}
     * da nova fonte, sob pena de desalinhar o furigana.
     */
    public static final double EM_PER_FONT_SIZE = 1000.0 / 1448.0;

    /** Largura de um caractere de largura total (um "quadrado" da grade). */
    public static final double CELL_WIDTH = MAIN_FONT_SIZE * EM_PER_FONT_SIZE;

    /**
     * Avanço, em "em", dos caracteres ASCII imprimíveis de {@code U+0020} a
     * {@code U+007E}, na fonte do template.
     *
     * <p>Valores lidos da tabela {@code hmtx} de {@code Noto Sans CJK JP}
     * ({@code unitsPerEm = 1000}) e normalizados pelo "em" (o avanço de um
     * caractere de largura total). A fonte renderiza o latim de forma
     * proporcional — por exemplo, o espaço tem {@code 0.224} e o {@code "}"} tem
     * {@code 0.338} —, bem distante de uma largura fixa de meio "em". Usar os
     * avanços reais evita deslocamentos cumulativos nas linhas com pontuação ou
     * texto latino.
     *
     * <p><strong>Atenção:</strong> a tabela é específica da fonte do template.
     * Trocar a fonte exige recalculá-la a partir das métricas {@code hmtx} da
     * nova fonte.
     */
    private static final double[] ASCII_ADVANCES = {
            0.224, 0.323, 0.474, 0.555, 0.555, 0.921, 0.680, 0.278, // 0x20..0x27
            0.338, 0.338, 0.467, 0.555, 0.278, 0.347, 0.278, 0.392, // 0x28..0x2F
            0.555, 0.555, 0.555, 0.555, 0.555, 0.555, 0.555, 0.555, // 0x30..0x37
            0.555, 0.555, 0.278, 0.278, 0.555, 0.555, 0.555, 0.474, // 0x38..0x3F
            0.946, 0.608, 0.657, 0.638, 0.688, 0.589, 0.552, 0.689, // 0x40..0x47
            0.728, 0.293, 0.535, 0.646, 0.543, 0.812, 0.723, 0.742, // 0x48..0x4F
            0.633, 0.742, 0.635, 0.596, 0.599, 0.721, 0.575, 0.878, // 0x50..0x57
            0.573, 0.531, 0.603, 0.338, 0.392, 0.338, 0.555, 0.559, // 0x58..0x5F
            0.606, 0.563, 0.618, 0.510, 0.620, 0.554, 0.325, 0.564, // 0x60..0x67
            0.607, 0.275, 0.275, 0.552, 0.284, 0.926, 0.610, 0.606, // 0x68..0x6F
            0.620, 0.620, 0.388, 0.468, 0.377, 0.607, 0.521, 0.802, // 0x70..0x77
            0.498, 0.521, 0.475, 0.338, 0.270, 0.338, 0.555, // 0x78..0x7E
    };

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
        // Os glifos CJK vivem no "em", não na caixa de linha: esta fonte tem
        // altura de linha de 1,448 "em" e um "leading" interno grande. Posicionar
        // as caixas de linha deixa o furigana longe da palavra; posicionar os
        // "em" (CELL_WIDTH para o Main, FURIGANA_FONT_SIZE * EM_PER_FONT_SIZE para
        // o Furigana) com uma folga pequena o mantém próximo do kanji.
        double offset = CELL_WIDTH / 2.0 + FURIGANA_GAP + FURIGANA_FONT_SIZE * EM_PER_FONT_SIZE / 2.0;
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
     * Largura de um caractere na grade, em unidades de "em" (largura total).
     *
     * <p>Caracteres ASCII usam o avanço real da fonte do template
     * ({@link #ASCII_ADVANCES}); alguns sinais latinos aplicáveis a legendas
     * (aspas curvas e travessões) também; o katakana half-width e a pontuação
     * half-width ocupam meio "em"; os demais (kanji, kana, pontuação japonesa)
     * ocupam um "em". Caracteres de controle não deslocam a grade.
     *
     * @param c caractere a medir
     * @return largura em "em"
     */
    public static double cellWidth(char c) {
        if (c < ' ' || c == '\u007F') {
            // Controles (ex.: '\r' residual) não devem existir no texto.
            return 0.0;
        }
        if (c <= '~') {
            return ASCII_ADVANCES[c - ' '];
        }
        switch (c) {
            case '\u2018': // ‘
            case '\u2019': // ’
                return 0.278;
            case '\u201C': // “
            case '\u201D': // ”
                return 0.474;
            case '\u2013': // –
                return 0.536;
            case '\u2014': // —
                return 0.894;
            case '\u2E3A': // ⸺
                return 1.676;
            default:
                break;
        }
        if (c >= '\uFF61' && c <= '\uFF9F') {
            // Katakana half-width e pontuação half-width.
            return 0.5;
        }
        return 1.0;
    }
}
