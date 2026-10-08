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
        assertEquals(0.5, AssLayout.cellWidth('a'));
        assertEquals(0.5, AssLayout.cellWidth('7'));
        assertEquals(1.0, AssLayout.cellWidth('日'));
        assertEquals(1.0, AssLayout.cellWidth('あ'));
        assertEquals(0.5, AssLayout.cellWidth('ｶ'));
    }

    @Test
    @DisplayName("Soma a largura de um texto em células")
    void sumsTextCells() {
        assertEquals(3.0, AssLayout.cells("日本語"));
        assertEquals(1.5, AssLayout.cells("abc"));
        assertEquals(1.5, AssLayout.cells("日a"));
        assertEquals(0.0, AssLayout.cells(""));
        assertEquals(0.0, AssLayout.cells(null));
    }

    @Test
    @DisplayName("Centraliza a linha na tela")
    void centersTheLine() {
        // Uma linha de 3 células começa em (1920 - 192) / 2 = 864; o centro é 960.
        assertEquals(960.0, AssLayout.xForCenterCell(1.5, 3.0));
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
