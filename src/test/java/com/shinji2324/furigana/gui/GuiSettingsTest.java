package com.shinji2324.furigana.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GuiSettings}, the file-backed settings store of the GUI.
 */
class GuiSettingsTest {

    @Test
    @DisplayName("lastDirectory is null before anything is stored")
    void emptyByDefault(@TempDir Path dir) {
        GuiSettings settings = new GuiSettings(dir.resolve("gui.properties"));

        assertNull(settings.lastDirectory());
    }

    @Test
    @DisplayName("lastDirectory is persisted and read back")
    void storesAndReads(@TempDir Path dir) {
        Path file = dir.resolve("nested").resolve("gui.properties");
        GuiSettings settings = new GuiSettings(file);

        settings.lastDirectory("/home/user/videos");

        assertEquals("/home/user/videos", settings.lastDirectory());
        assertTrue(Files.isRegularFile(file));
        // A fresh store reading the same file sees the value too.
        assertEquals("/home/user/videos", new GuiSettings(file).lastDirectory());
    }

    @Test
    @DisplayName("lastDirectory can be cleared")
    void clearsValue(@TempDir Path dir) {
        GuiSettings settings = new GuiSettings(dir.resolve("gui.properties"));
        settings.lastDirectory("/tmp");

        settings.lastDirectory(null);

        assertNull(settings.lastDirectory());
    }

    @Test
    @DisplayName("An unreadable file does not break the store")
    void toleratesUnreadableFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gui.properties");
        Files.writeString(file, "broken=\\uZZZZ\n");

        GuiSettings settings = new GuiSettings(file);

        // Must not throw; an unparsable file simply yields the defaults.
        assertNull(settings.lastDirectory());
    }
}
