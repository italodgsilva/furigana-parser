package com.shinji2324.furigana.font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SfntReader}: reading the family name and separating the faces
 * of a collection.
 */
class SfntReaderTest {

    @Test
    @DisplayName("Reads the family of a standalone sfnt")
    void readsSingleFace() {
        List<SfntReader.Face> faces = SfntReader.read(TestFonts.sfnt("My Font"));

        assertEquals(1, faces.size());
        assertEquals(Set.of("My Font"), faces.get(0).families());
        assertEquals(-1, faces.get(0).faceIndex());
        assertFalse(faces.get(0).cff());
    }

    @Test
    @DisplayName("Separates the faces of a collection and identifies each family")
    void separatesCollectionFaces() {
        byte[] collection = TestFonts.ttc("Font A", "Font B");

        List<SfntReader.Face> faces = SfntReader.read(collection);

        assertEquals(2, faces.size());
        assertEquals(Set.of("Font A"), faces.get(0).families());
        assertEquals(0, faces.get(0).faceIndex());
        assertEquals(Set.of("Font B"), faces.get(1).families());
        assertEquals(1, faces.get(1).faceIndex());
    }

    @Test
    @DisplayName("The face extracted from the collection equals the standalone sfnt")
    void extractsFaceAsStandaloneSfnt() {
        byte[] collection = TestFonts.ttc("Font A", "Font B");

        List<SfntReader.Face> faces = SfntReader.read(collection);

        // The extracted content must be identical to the equivalent standalone font.
        assertArrayEquals(TestFonts.sfnt("Font B"), faces.get(1).data());
        assertTrue(SfntReader.isSfnt(faces.get(1).data(), 0));
    }

    @Test
    @DisplayName("Ignores content that is not an sfnt")
    void ignoresNonSfntContent() {
        assertTrue(SfntReader.read(null).isEmpty());
        assertTrue(SfntReader.read(new byte[] {1, 2, 3}).isEmpty());
        assertTrue(SfntReader.read(new byte[64]).isEmpty());
    }
}
