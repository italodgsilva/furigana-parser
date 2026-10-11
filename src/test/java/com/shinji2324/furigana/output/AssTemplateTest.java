package com.shinji2324.furigana.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link AssTemplate}: conteúdo padrão e nomes de família dos estilos.
 */
class AssTemplateTest {

    @Test
    @DisplayName("Lista os nomes de família distintos dos estilos")
    void listsDistinctFontFamilies() {
        AssTemplate template = new AssTemplate(String.join("\n",
                "[V4+ Styles]",
                "Style: Main,Noto Sans CJK JP,64,&H00FFFFFF,...,1",
                "Style: Furigana,Noto Sans CJK JP,32,&H00FFFFFF,...,1"));

        assertEquals(List.of("Noto Sans CJK JP"), template.fontFamilies());
    }

    @Test
    @DisplayName("Sem estilos, não há famílias")
    void returnsEmptyWithoutStyles() {
        assertTrue(new AssTemplate("[Script Info]\nTitle: X\n").fontFamilies().isEmpty());
    }

    @Test
    @DisplayName("O template padrão usa a fonte Noto Sans CJK JP")
    void defaultTemplateUsesNotoCjk() {
        assertEquals(List.of("Noto Sans CJK JP"),
                AssTemplate.defaultTemplate().fontFamilies());
    }
}
