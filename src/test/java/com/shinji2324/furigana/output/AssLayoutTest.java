package com.shinji2324.furigana.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes do {@link AssLayout} (grade de caracteres e coordenadas).
 */
class AssLayoutTest {

    @Test
    @DisplayName("Mede a largura de caracteres conforme a grade CJK")
    void measuresCharacterWidths() {
        assertEquals(1.0, AssLayout.cellWidth('日'));
        assertEquals(1.0, AssLayout.cellWidth('あ'));
        assertEquals(1.0, AssLayout.cellWidth('　')); // espaço ideográfico
        assertEquals(0.5, AssLayout.cellWidth('ｶ')); // katakana half-width
        assertEquals(0.0, AssLayout.cellWidth('\r')); // controle não desloca
    }

    @Test
    @DisplayName("Usa o avanço real da fonte para caracteres ASCII")
    void measuresAsciiAdvances() {
        // Avanços reais de "Noto Sans CJK JP" (hmtx / unitsPerEm).
        assertEquals(0.224, AssLayout.cellWidth(' '));
        assertEquals(0.338, AssLayout.cellWidth('('));
        assertEquals(0.338, AssLayout.cellWidth(')'));
        assertEquals(0.555, AssLayout.cellWidth('7'));
        assertEquals(0.563, AssLayout.cellWidth('a'));
        assertEquals(0.474, AssLayout.cellWidth('“'));
        assertEquals(0.474, AssLayout.cellWidth('”'));
    }

    @Test
    @DisplayName("Soma a largura de um texto em células")
    void sumsTextCells() {
        assertEquals(3.0, AssLayout.cells("日本語"), 1e-9);
        assertEquals(1.691, AssLayout.cells("abc"), 1e-9);
        assertEquals(1.563, AssLayout.cells("日a"), 1e-9);
        assertEquals(8.224, AssLayout.cells("世界で 一番きれい"), 1e-9);
        assertEquals(0.0, AssLayout.cells(""));
        assertEquals(0.0, AssLayout.cells(null));
    }

    @Test
    @DisplayName("Centraliza a linha na tela")
    void centersTheLine() {
        // Uma linha de 3 células começa em (1920 - 3 * CELL_WIDTH) / 2; o centro é 960.
        assertEquals(960.0, AssLayout.xForCenterCell(1.5, 3.0));
    }

    @Test
    @DisplayName("Usa o 'em' real do libass como largura de célula")
    void usesLibassEmAsCellWidth() {
        // libass interpreta Fontsize como a altura da linha (hhea), não como o "em":
        // para Noto Sans CJK JP, em = Fontsize * 1000 / (1160 + 288).
        assertEquals(1000.0 / 1448.0, AssLayout.EM_PER_FONT_SIZE);
        assertEquals(AssLayout.MAIN_FONT_SIZE * AssLayout.EM_PER_FONT_SIZE, AssLayout.CELL_WIDTH);
    }

    @Test
    @DisplayName("Empilha as linhas de baixo para cima")
    void stacksLinesFromBottom() {
        assertEquals(988.0, AssLayout.mainCenterY(0));
        assertEquals(868.0, AssLayout.mainCenterY(1));
        assertEquals(928.0, AssLayout.furiganaCenterY(0));
        assertEquals(808.0, AssLayout.furiganaCenterY(1));
    }
}
