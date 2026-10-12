package com.shinji2324.furigana.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link AssLayout} (character grid and coordinates).
 */
class AssLayoutTest {

    @Test
    @DisplayName("Measures character widths according to the CJK grid")
    void measuresCharacterWidths() {
        assertEquals(1.0, AssLayout.cellWidth('日'));
        assertEquals(1.0, AssLayout.cellWidth('あ'));
        assertEquals(1.0, AssLayout.cellWidth('　')); // ideographic space
        assertEquals(0.5, AssLayout.cellWidth('ｶ')); // katakana half-width
        assertEquals(0.0, AssLayout.cellWidth('\r')); // a control character does not shift
    }

    @Test
    @DisplayName("Uses the font's real advance for ASCII characters")
    void measuresAsciiAdvances() {
        // Real advances of "Noto Sans CJK JP" (hmtx / unitsPerEm).
        assertEquals(0.224, AssLayout.cellWidth(' '));
        assertEquals(0.338, AssLayout.cellWidth('('));
        assertEquals(0.338, AssLayout.cellWidth(')'));
        assertEquals(0.555, AssLayout.cellWidth('7'));
        assertEquals(0.563, AssLayout.cellWidth('a'));
        assertEquals(0.474, AssLayout.cellWidth('“'));
        assertEquals(0.474, AssLayout.cellWidth('”'));
    }

    @Test
    @DisplayName("Sums the width of a text in cells")
    void sumsTextCells() {
        assertEquals(3.0, AssLayout.cells("日本語"), 1e-9);
        assertEquals(1.691, AssLayout.cells("abc"), 1e-9);
        assertEquals(1.563, AssLayout.cells("日a"), 1e-9);
        assertEquals(8.224, AssLayout.cells("世界で 一番きれい"), 1e-9);
        assertEquals(0.0, AssLayout.cells(""));
        assertEquals(0.0, AssLayout.cells(null));
    }

    @Test
    @DisplayName("Centers the line on screen")
    void centersTheLine() {
        // A 3-cell line starts at (1920 - 3 * CELL_WIDTH) / 2; the center is 960.
        assertEquals(960.0, AssLayout.xForCenterCell(1.5, 3.0));
    }

    @Test
    @DisplayName("Uses libass's real 'em' as the cell width")
    void usesLibassEmAsCellWidth() {
        // libass interprets Fontsize as the line height (hhea), not as the "em":
        // for Noto Sans CJK JP, em = Fontsize * 1000 / (1160 + 288).
        assertEquals(1000.0 / 1448.0, AssLayout.EM_PER_FONT_SIZE);
        assertEquals(AssLayout.MAIN_FONT_SIZE * AssLayout.EM_PER_FONT_SIZE, AssLayout.CELL_WIDTH);
    }

    @Test
    @DisplayName("Stacks the lines from bottom to top")
    void stacksLinesFromBottom() {
        assertEquals(988.0, AssLayout.mainCenterY(0));
        assertEquals(904.0, AssLayout.mainCenterY(1));
        double em = AssLayout.EM_PER_FONT_SIZE;
        double offset = AssLayout.CELL_WIDTH / 2.0 + AssLayout.FURIGANA_GAP
                + AssLayout.FURIGANA_FONT_SIZE * em / 2.0;
        assertEquals(988.0 - offset, AssLayout.furiganaCenterY(0), 1e-9);
        assertEquals(904.0 - offset, AssLayout.furiganaCenterY(1), 1e-9);
    }
}
