package com.shinji2324.furigana.font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FontLocator}: search by family, normalization and extraction
 * of a face from a collection.
 */
class FontLocatorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Locates the font by family, ignoring case and whitespace")
    void findsByFamily() throws IOException {
        byte[] font = TestFonts.sfnt("My Font");
        Files.write(tempDir.resolve("some-file.ttf"), font);

        Optional<FontFile> found = new FontLocator(List.of(tempDir)).find("  my   font ");

        assertTrue(found.isPresent());
        assertEquals("some-file.ttf", found.get().fileName());
        assertArrayEquals(font, found.get().data());
    }

    @Test
    @DisplayName("Does not find a nonexistent family")
    void doesNotFindUnknownFamily() throws IOException {
        Files.write(tempDir.resolve("f.ttf"), TestFonts.sfnt("Other"));

        assertTrue(new FontLocator(List.of(tempDir)).find("My Font").isEmpty());
    }

    @Test
    @DisplayName("Extracts the right face from a collection and renames the file")
    void extractsFaceFromCollection() throws IOException {
        Files.write(tempDir.resolve("col.ttc"), TestFonts.ttc("Font A", "Font B"));

        Optional<FontFile> found = new FontLocator(List.of(tempDir)).find("Font B");

        assertTrue(found.isPresent());
        assertEquals("col-face1.ttf", found.get().fileName());
        assertArrayEquals(TestFonts.sfnt("Font B"), found.get().data());
    }

    @Test
    @DisplayName("Reads an explicitly given file")
    void readsExplicitFile() throws IOException {
        Path file = tempDir.resolve("custom.otf");
        byte[] font = TestFonts.sfnt("Chosen");
        Files.write(file, font);

        FontFile loaded = new FontLocator(List.<Path>of()).read(file, "Chosen");

        assertEquals("custom.otf", loaded.fileName());
        assertArrayEquals(font, loaded.data());
    }

    @Test
    @DisplayName("Rejects an explicitly given file that is not a font")
    void rejectsInvalidExplicitFile() throws IOException {
        Path file = tempDir.resolve("x.ttf");
        Files.write(file, new byte[] {1, 2, 3, 4});

        assertThrows(IOException.class, () -> new FontLocator(List.<Path>of()).read(file, "Any"));
    }

    @Test
    @DisplayName("Clear error when the given file does not exist")
    void reportsMissingExplicitFile() {
        Path missing = tempDir.resolve("missing.otf");

        IOException error = assertThrows(IOException.class,
                () -> new FontLocator(List.<Path>of()).read(missing, "Any"));

        assertTrue(error.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Does not scan nonexistent directories")
    void ignoresMissingDirectory() {
        assertTrue(new FontLocator(List.of(tempDir.resolve("missing"))).find("Any").isEmpty());
    }
}
