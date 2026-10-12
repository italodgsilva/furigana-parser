package com.shinji2324.furigana.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AssTemplate}: default content and style family names.
 */
class AssTemplateTest {

    @Test
    @DisplayName("Lists the distinct family names of the styles")
    void listsDistinctFontFamilies() {
        AssTemplate template = new AssTemplate(String.join("\n",
                "[V4+ Styles]",
                "Style: Main,Noto Sans CJK JP,64,&H00FFFFFF,...,1",
                "Style: Furigana,Noto Sans CJK JP,32,&H00FFFFFF,...,1"));

        assertEquals(List.of("Noto Sans CJK JP"), template.fontFamilies());
    }

    @Test
    @DisplayName("Without styles, there are no families")
    void returnsEmptyWithoutStyles() {
        assertTrue(new AssTemplate("[Script Info]\nTitle: X\n").fontFamilies().isEmpty());
    }

    @Test
    @DisplayName("The default template uses the Noto Sans CJK JP font")
    void defaultTemplateUsesNotoCjk() {
        assertEquals(List.of("Noto Sans CJK JP"),
                AssTemplate.defaultTemplate().fontFamilies());
    }
}
