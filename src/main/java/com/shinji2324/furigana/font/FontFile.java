package com.shinji2324.furigana.font;

/**
 * Uma fonte localizada, pronta para ser embutida em um ASS.
 *
 * <p>O {@code data} contém os bytes de uma única face (um arquivo {@code .ttf}
 * ou {@code .otf} autônomo). Quando a fonte original é uma coleção ({@code .ttc}),
 * a face correspondente já vem extraída, pois o ASS embute uma face por vez.
 *
 * @param fileName nome sugerido para a fonte, usado no cabeçalho
 *                 {@code fontname:} da seção {@code [Fonts]}
 * @param data     conteúdo binário da face da fonte
 */
public record FontFile(String fileName, byte[] data) {
}
