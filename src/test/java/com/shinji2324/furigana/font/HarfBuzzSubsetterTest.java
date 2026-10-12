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
 * Tests for {@link HarfBuzzSubsetter}.
 *
 * <p>The call flow is exercised with a fake implementation of the JNA
 * interface, without requiring the native library. The test with the real
 * library only runs when {@code libharfbuzz-subset} and a sample font are
 * available.
 */
class HarfBuzzSubsetterTest {

    @Test
    @DisplayName("Reads the subset bytes and releases HarfBuzz resources")
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
    @DisplayName("Returns null when subsetting fails, releasing the input blob")
    void returnsNullOnFailure() {
        FakeHarfBuzz fake = new FakeHarfBuzz(new byte[] {1});
        fake.failSubset = true;
        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(fake);

        assertNull(subsetter.subset(new byte[] {1, 2, 3}, Set.of(65)));
        assertTrue(fake.destroyed.contains("blob"));
        assertFalse(fake.destroyed.contains("resultBlob"));
    }

    @Test
    @DisplayName("Returns null when a native symbol cannot be resolved at call time")
    void returnsNullWhenNativeSymbolMissing() {
        FakeHarfBuzz fake = new FakeHarfBuzz(new byte[] {1});
        fake.throwOnBlobCreate = true;
        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(fake);

        assertNull(subsetter.subset(new byte[] {1, 2, 3}, Set.of(65)));
    }

    @Test
    @DisplayName("Routes each function to the core or the subset library (Windows split)")
    void combinesCoreAndSubsetLibraries() {
        byte[] output = {1, 2, 3, 4, 5};
        FakeCore core = new FakeCore(output);
        FakeSubset subset = new FakeSubset();
        HarfBuzzSubsetLibrary.HarfBuzz harfBuzz = HarfBuzzSubsetLibrary.combine(core, subset);

        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(harfBuzz);
        byte[] result = subsetter.subset(new byte[] {9, 9, 9}, Set.of(0x65E5, 0x672C));

        assertArrayEquals(output, result);
        assertEquals(1, core.blobCreates);
        assertEquals(1, subset.subsetCalls);
        assertEquals(Set.of(0x65E5, 0x672C), core.unicodes);
        assertEquals(NO_HINTING, subset.flags);
        assertTrue(core.destroyed.containsAll(List.of("blob", "face", "resultBlob", "subsetFace")));
        assertTrue(subset.destroyed.contains("input"));
    }

    @Test
    @DisplayName("Combined loading only accepts libraries that expose the required symbols")
    void loadRejectsDllPairsWithoutCoreFunctions() {
        // A subsetting candidate whose core routine cannot be resolved must be
        // rejected (the Windows case), not returned for later calls to fail on.
        Optional<HarfBuzzSubsetLibrary.HarfBuzz> library = HarfBuzzSubsetLibrary.load(
                List.of(), List.of());
        assertTrue(library.isEmpty());
    }

    @Test
    @DisplayName("Empty inputs do not call the library")
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
    @DisplayName("load returns empty when there is no library in the directories")
    void loadReturnsEmptyWhenMissing(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("libharfbuzz.so.0"), "not the subset library");
        Files.writeString(directory.resolve("other-library.so"), "irrelevant");
        Files.writeString(directory.resolve("libharfbuzz-subset.so.0"), "invalid file");

        assertTrue(HarfBuzzSubsetLibrary.load(List.of(), List.of(directory)).isEmpty());
        assertTrue(HarfBuzzSubsetLibrary.load(List.of("nonexistent-name-xyz"), List.of(directory))
                .isEmpty());
    }

    @Test
    @DisplayName("The installation hint explains the reason and how to fix it")
    void installationHintIsHelpful() {
        String hint = HarfBuzzSubsetter.installationHint();

        assertFalse(hint.isBlank());
        assertTrue(hint.contains("libharfbuzz-subset"), "should mention the missing library");
        assertTrue(hint.contains("without size reduction"), "should explain the effect");
        assertTrue(hint.contains("HarfBuzz"), "should suggest installing HarfBuzz");
        assertTrue(hint.contains(HarfBuzzSubsetLibrary.OVERRIDE_ENV),
                "should mention the environment variable");
    }

    @Test
    @DisplayName("Subsets a real font when the library and the font are available")
    void subsetsRealFontWhenAvailable() throws IOException {
        Optional<HarfBuzzSubsetLibrary.HarfBuzz> library = HarfBuzzSubsetLibrary.load();
        Path font = Path.of(System.getProperty("user.home"), ".fonts", "NotoSansJP.otf");
        assumeTrue(library.isPresent(), "libharfbuzz-subset not available");
        assumeTrue(Files.isRegularFile(font), "sample font not available: " + font);

        HarfBuzzSubsetter subsetter = new HarfBuzzSubsetter(library.get());
        byte[] full = Files.readAllBytes(font);
        Set<Integer> codePoints = "日本語あいうえお漢字".codePoints().boxed()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        byte[] reduced = subsetter.subset(full, codePoints);

        assertNotNull(reduced);
        assertTrue(reduced.length < full.length, "the subset must be smaller");
        assertTrue(SfntReader.isSfnt(reduced, 0), "the output must still be sfnt");
        assertFalse(SfntReader.readFamilies(reduced, 0).isEmpty(),
                "the family must be preserved so the player can match the style");
    }

    /** Value of {@code HB_SUBSET_FLAGS_NO_HINTING}. */
    private static final int NO_HINTING = 0x1;

    /** Fake implementation of the library, only to exercise the JNA flow. */
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
        private boolean throwOnBlobCreate;
        private int blobCreates;
        private int flags = -1;

        FakeHarfBuzz(byte[] output) {
            this.output = output;
        }

        @Override
        public Pointer hb_blob_create(Pointer data, int length, int mode, Pointer userData,
                                      Pointer destroy) {
            blobCreates++;
            if (throwOnBlobCreate) {
                throw new UnsatisfiedLinkError("hb_blob_create not found");
            }
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

    /** Fake core library (blob/face/set functions of {@code libharfbuzz}). */
    private static final class FakeCore implements HarfBuzzSubsetLibrary.CoreHarfBuzz {

        private final Memory blob = new Memory(1);
        private final Memory face = new Memory(1);
        private final Memory resultBlob = new Memory(1);
        private final byte[] output;
        private final Set<Integer> unicodes = new LinkedHashSet<>();
        private final Set<String> destroyed = new LinkedHashSet<>();

        private int blobCreates;

        FakeCore(byte[] output) {
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
        public void hb_blob_destroy(Pointer target) {
            destroyed.add(target == blob ? "blob" : "resultBlob");
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
        public void hb_face_destroy(Pointer target) {
            destroyed.add(target == face ? "face" : "subsetFace");
        }

        @Override
        public void hb_set_add(Pointer set, int codePoint) {
            unicodes.add(codePoint);
        }
    }

    /** Fake subset library ({@code hb_subset_*} functions). */
    private static final class FakeSubset implements HarfBuzzSubsetLibrary.HarfBuzzSubsetApi {

        private final Memory input = new Memory(1);
        private final Memory subsetFace = new Memory(1);
        private final Memory unicodeSet = new Memory(1);
        private final Set<String> destroyed = new LinkedHashSet<>();

        private int subsetCalls;
        private int flags = -1;

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
            subsetCalls++;
            return subsetFace;
        }
    }
}
