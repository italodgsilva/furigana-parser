package com.shinji2324.furigana.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("Changes the primary color of styles with a hex color")
    void changesColorWithHex() {
        AssTemplate template = new AssTemplate(String.join("\n",
                "[V4+ Styles]",
                "Style: Main,Noto Sans CJK JP,64,&H00FFFFFF,...,1",
                "Style: Furigana,Noto Sans CJK JP,32,&H00FFFFFF,...,1"));
        AssTemplate colored = AssTemplate.withColor(template, "#FF0000");
        assertTrue(colored.content().contains("&H000000FF"));
        assertEquals(List.of("Noto Sans CJK JP"), colored.fontFamilies());
    }

    @Test
    @DisplayName("Changes the primary color with named color")
    void changesColorWithName() {
        AssTemplate template = new AssTemplate(String.join("\n",
                "[V4+ Styles]",
                "Style: Main,Noto Sans CJK JP,64,&H00FFFFFF,...,1"));
        AssTemplate colored = AssTemplate.withColor(template, "yellow");
        assertTrue(colored.content().contains("&H0000FFFF"));
    }

    @Test
    @DisplayName("Returns original template when color is invalid")
    void returnsOriginalForInvalidColor() {
        AssTemplate template = AssTemplate.defaultTemplate();
        AssTemplate colored = AssTemplate.withColor(template, "not-a-color");
        assertEquals(template.content(), colored.content());
    }

    @Test
    @DisplayName("Changes only PrimaryColour, keeping the other color fields")
    void changesOnlyPrimaryColour() {
        AssTemplate template = new AssTemplate(String.join("\n",
                "[V4+ Styles]",
                "Style: Main,Noto Sans CJK JP,64,&H00FFFFFF,&H11111111,&H22222222,&H80000000,...,1"));
        AssTemplate colored = AssTemplate.withColor(template, "#FF0000");

        String content = colored.content();
        assertTrue(content.contains(
                "&H000000FF,&H11111111,&H22222222,&H80000000"), content);
        assertFalse(content.contains("&H00FFFFFF"), content);
    }

    @Test
    @DisplayName("Keeps the trailing newline structure of the template")
    void keepsTrailingNewline() {
        AssTemplate template = AssTemplate.defaultTemplate();
        String before = template.content();
        String after = AssTemplate.withColor(template, "yellow").content();
        assertEquals(before.endsWith("\n"), after.endsWith("\n"));
    }
}
