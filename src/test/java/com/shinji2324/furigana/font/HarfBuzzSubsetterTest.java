package com.shinji2324.furigana.font;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Testes do {@link HarfBuzzSubsetter}.
 *
 * <p>O fluxo de chamadas é exercitado com uma implementação falsa da interface
 * JNA, sem exigir a biblioteca nativa. O teste com a biblioteca real só roda
 * quando {@code libharfbuzz-subset} e uma fonte de exemplo estão disponíveis.
 */
class HarfBuzzSubsetterTest {

    @Test
    @DisplayName("Lê os bytes reduzidos e libera os recursos do HarfBuzz")
    void readsSubsetBytesAndReleases() {
        byte[] output = {1, 2, 3, 4, 5};
        FakeHarfBuzz fake = new FakeHarfBuzz(output);
        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(fake);

        byte[] result = subsetter.subset(new byte[] {9, 9, 9}, Set.of(0x65E5, 0x672C));

        assertArrayEquals(output, result);
        assertEquals(Set.of(0x65E5, 0x672C), fake.unicodes);
        assertTrue(fake.destroyed.containsAll(
                Set.of("blob", "face", "input", "subsetFace", "resultBlob")));
        assertEquals(NO_HINTING, fake.flags);
    }

    @Test
    @DisplayName("Devolve null quando o subsetting falha, liberando o blob de entrada")
    void returnsNullOnFailure() {
        FakeHarfBuzz fake = new FakeHarfBuzz(new byte[] {1});
        fake.failSubset = true;
        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(fake);

        assertNull(subsetter.subset(new byte[] {1, 2, 3}, Set.of(65)));
        assertTrue(fake.destroyed.contains("blob"));
        assertFalse(fake.destroyed.contains("resultBlob"));
    }

    @Test
    @DisplayName("Entradas vazias não chamam a biblioteca")
    void skipsEmptyInput() {
        FakeHarfBuzz fake = new FakeHarfBuzz(new byte[] {1});
        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(fake);

        assertNull(subsetter.subset(null, Set.of(65)));
        assertNull(subsetter.subset(new byte[0], Set.of(65)));
        assertNull(subsetter.subset(new byte[] {1}, Set.of()));
        assertNull(subsetter.subset(new byte[] {1}, null));
        assertEquals(0, fake.blobCreates);
    }

    @Test
    @DisplayName("load devolve vazio quando não há biblioteca nos diretórios")
    void loadReturnsEmptyWhenMissing(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("libharfbuzz.so.0"), "não é a lib de subset");
        Files.writeString(directory.resolve("outra-biblioteca.so"), "irrelevante");
        Files.writeString(directory.resolve("libharfbuzz-subset.so.0"), "arquivo inválido");

        assertTrue(HarfBuzzSubsetLibrary.load(List.of(), List.of(directory)).isEmpty());
        assertTrue(HarfBuzzSubsetLibrary.load(List.of("nome-inexistente-xyz"), List.of(directory))
                .isEmpty());
    }

    @Test
    @DisplayName("Reduz uma fonte real quando a biblioteca e a fonte estão disponíveis")
    void subsetsRealFontWhenAvailable() throws IOException {
        Optional<HarfBuzzSubsetLibrary.HarfBuzz> library = HarfBuzzSubsetLibrary.load();
        Path font = Path.of(System.getProperty("user.home"), ".fonts", "NotoSansJP.otf");
        assumeTrue(library.isPresent(), "libharfbuzz-subset não disponível");
        assumeTrue(Files.isRegularFile(font), "fonte de exemplo não disponível: " + font);

        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(library.get());
        byte[] full = Files.readAllBytes(font);
        Set<Integer> codePoints = "日本語あいうえお漢字".codePoints().boxed()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        byte[] reduced = subsetter.subset(full, codePoints);

        assertNotNull(reduced);
        assertTrue(reduced.length < full.length, "o subconjunto deve ser menor");
        assertTrue(SfntReader.isSfnt(reduced, 0), "a saída deve continuar sendo sfnt");
        assertFalse(SfntReader.readFamilies(reduced, 0).isEmpty(),
                "a família deve ser preservada para o player casar o estilo");
    }

    /** Valor de {@code HB_SUBSET_FLAGS_NO_HINTING}. */
    private static final int NO_HINTING = 0x1;

    /** Implementação falsa da biblioteca, apenas para exercitar o fluxo JNA. */
    private static final class FakeHarfBuzz implements HarfBuzzSubsetLibrary.HarfBuzz {

        private final Memory blob = new Memory(1);
        private final Memory face = new Memory(1);
        private final Memory input = new Memory(1);
        private final Memory subsetFace = new Memory(1);
        private final Memory resultBlob = new Memory(1);
        private final Memory unicodeSet = new Memory(1);
        private final byte[] output;
        private final Set<Integer> unicodes = new LinkedHashSet<>();
        private final Set<String> destroyed = new LinkedHashSet<>();

        private boolean failSubset;
        private int blobCreates;
        private int flags = -1;

        FakeHarfBuzz(byte[] output) {
            this.output = output;
        }

        @Override
        public Pointer hb_blob_create(Pointer data, int length, int mode, Pointer userData,
                                      Pointer destroy) {
            blobCreates++;
            return blob;
        }

        @Override
        public Pointer hb_blob_get_data(Pointer blob, IntByReference length) {
            Memory memory = new Memory(output.length);
            memory.write(0, output, 0, output.length);
            length.setValue(output.length);
            return memory;
        }

        @Override
        public void hb_blob_destroy(Pointer blob) {
            destroyed.add(blob == resultBlob ? "resultBlob" : "blob");
        }

        @Override
        public Pointer hb_face_create(Pointer blob, int index) {
            return face;
        }

        @Override
        public Pointer hb_face_reference_blob(Pointer face) {
            return resultBlob;
        }

        @Override
        public void hb_face_destroy(Pointer face) {
            destroyed.add(face == subsetFace ? "subsetFace" : "face");
        }

        @Override
        public Pointer hb_subset_input_create_or_fail() {
            return input;
        }

        @Override
        public Pointer hb_subset_input_unicode_set(Pointer input) {
            return unicodeSet;
        }

        @Override
        public void hb_subset_input_set_flags(Pointer input, int flags) {
            this.flags = flags;
        }

        @Override
        public void hb_subset_input_destroy(Pointer input) {
            destroyed.add("input");
        }

        @Override
        public Pointer hb_subset_or_fail(Pointer face, Pointer input) {
            return failSubset ? null : subsetFace;
        }

        @Override
        public void hb_set_add(Pointer set, int codePoint) {
            unicodes.add(codePoint);
        }
    }
}
