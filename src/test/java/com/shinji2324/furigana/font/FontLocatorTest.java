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
 * Testes do {@link FontLocator}: busca por família, normalização e recorte de
 * face de uma coleção.
 */
class FontLocatorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Localiza a fonte pela família, ignorando maiúsculas e espaços")
    void findsByFamily() throws IOException {
        byte[] font = TestFonts.sfnt("Minha Fonte");
        Files.write(tempDir.resolve("arquivo-qualquer.ttf"), font);

        Optional<FontFile> found = new FontLocator(List.of(tempDir)).find("  minha   fonte ");

        assertTrue(found.isPresent());
        assertEquals("arquivo-qualquer.ttf", found.get().fileName());
        assertArrayEquals(font, found.get().data());
    }

    @Test
    @DisplayName("Não encontra família inexistente")
    void doesNotFindUnknownFamily() throws IOException {
        Files.write(tempDir.resolve("f.ttf"), TestFonts.sfnt("Outra"));

        assertTrue(new FontLocator(List.of(tempDir)).find("Minha Fonte").isEmpty());
    }

    @Test
    @DisplayName("Recorta a face certa de uma coleção e renomeia o arquivo")
    void extractsFaceFromCollection() throws IOException {
        Files.write(tempDir.resolve("col.ttc"), TestFonts.ttc("Fonte A", "Fonte B"));

        Optional<FontFile> found = new FontLocator(List.of(tempDir)).find("Fonte B");

        assertTrue(found.isPresent());
        assertEquals("col-face1.ttf", found.get().fileName());
        assertArrayEquals(TestFonts.sfnt("Fonte B"), found.get().data());
    }

    @Test
    @DisplayName("Lê um arquivo informado explicitamente")
    void readsExplicitFile() throws IOException {
        Path file = tempDir.resolve("custom.otf");
        byte[] font = TestFonts.sfnt("Escolhida");
        Files.write(file, font);

        FontFile loaded = new FontLocator(List.<Path>of()).read(file, "Escolhida");

        assertEquals("custom.otf", loaded.fileName());
        assertArrayEquals(font, loaded.data());
    }

    @Test
    @DisplayName("Rejeita arquivo explicitamente informado que não é fonte")
    void rejectsInvalidExplicitFile() throws IOException {
        Path file = tempDir.resolve("x.ttf");
        Files.write(file, new byte[] {1, 2, 3, 4});

        assertThrows(IOException.class, () -> new FontLocator(List.<Path>of()).read(file, "Qualquer"));
    }

    @Test
    @DisplayName("Erro claro quando o arquivo informado não existe")
    void reportsMissingExplicitFile() {
        Path missing = tempDir.resolve("nao-existe.otf");

        IOException error = assertThrows(IOException.class,
                () -> new FontLocator(List.<Path>of()).read(missing, "Qualquer"));

        assertTrue(error.getMessage().contains("não encontrado"));
    }

    @Test
    @DisplayName("Não varre diretórios inexistentes")
    void ignoresMissingDirectory() {
        assertTrue(new FontLocator(List.of(tempDir.resolve("nao-existe"))).find("Qualquer").isEmpty());
    }
}
