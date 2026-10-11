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
 * Testes do {@link SfntReader}: leitura do nome de família e separação das faces
 * de uma coleção.
 */
class SfntReaderTest {

    @Test
    @DisplayName("Lê a família de um sfnt autônomo")
    void readsSingleFace() {
        List<SfntReader.Face> faces = SfntReader.read(TestFonts.sfnt("Minha Fonte"));

        assertEquals(1, faces.size());
        assertEquals(Set.of("Minha Fonte"), faces.get(0).families());
        assertEquals(-1, faces.get(0).faceIndex());
        assertFalse(faces.get(0).cff());
    }

    @Test
    @DisplayName("Separa as faces de uma coleção e identifica a família de cada uma")
    void separatesCollectionFaces() {
        byte[] collection = TestFonts.ttc("Fonte A", "Fonte B");

        List<SfntReader.Face> faces = SfntReader.read(collection);

        assertEquals(2, faces.size());
        assertEquals(Set.of("Fonte A"), faces.get(0).families());
        assertEquals(0, faces.get(0).faceIndex());
        assertEquals(Set.of("Fonte B"), faces.get(1).families());
        assertEquals(1, faces.get(1).faceIndex());
    }

    @Test
    @DisplayName("A face recortada da coleção equivale ao sfnt autônomo")
    void extractsFaceAsStandaloneSfnt() {
        byte[] collection = TestFonts.ttc("Fonte A", "Fonte B");

        List<SfntReader.Face> faces = SfntReader.read(collection);

        // O conteúdo recortado deve ser idêntico à fonte autônoma equivalente.
        assertArrayEquals(TestFonts.sfnt("Fonte B"), faces.get(1).data());
        assertTrue(SfntReader.isSfnt(faces.get(1).data(), 0));
    }

    @Test
    @DisplayName("Ignora conteúdo que não é um sfnt")
    void ignoresNonSfntContent() {
        assertTrue(SfntReader.read(null).isEmpty());
        assertTrue(SfntReader.read(new byte[] {1, 2, 3}).isEmpty());
        assertTrue(SfntReader.read(new byte[64]).isEmpty());
    }
}
