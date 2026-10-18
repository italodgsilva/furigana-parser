package com.shinji2324.furigana.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the platform font mappings that {@link GuiMain} unpacks into
 * {@code ${java.home}/lib} in a native image. They are ordinary resources, so
 * nothing else would notice if they stopped being packaged.
 */
class FontConfigResourcesTest {

    @Test
    @DisplayName("The Windows font mapping is bundled and readable")
    void windowsFontMappingIsBundled() throws Exception {
        assertFontMapping("/com/shinji2324/furigana/gui/awt/fontconfig-windows.properties");
    }

    @Test
    @DisplayName("The macOS font mapping is bundled and readable")
    void macosFontMappingIsBundled() throws Exception {
        assertFontMapping("/com/shinji2324/furigana/gui/awt/fontconfig-macos.properties");
    }

    private static void assertFontMapping(String resource) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = FontConfigResourcesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing from the class path");
            properties.load(in);
        }
        assertEquals("1", properties.getProperty("version"), "version in " + resource);
        assertFalse(properties.getProperty("sequence.allfonts", "").isBlank(),
                "sequence.allfonts in " + resource);
    }
}
