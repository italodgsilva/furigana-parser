package com.shinji2324.furigana.font;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * HarfBuzz-based font subsetter.
 *
 * <p>It receives an sfnt face and a set of code points and returns the face
 * reduced to the corresponding glyphs (plus those HarfBuzz keeps as
 * dependencies, such as {@code .notdef} and composite targets). The output
 * format is the same as the input: a standalone sfnt.
 *
 * <p>Hinting is discarded ({@code HB_SUBSET_FLAGS_NO_HINTING}) to reduce the
 * file; the outlines and the {@code name} table — needed for the player to match
 * the font with the style's {@code Fontname} — are preserved.
 *
 * <p>Any HarfBuzz failure (missing library, invalid input, unsupported font)
 * results in {@code null}, signaling to the caller to embed the full font.
 */
public final class HarfBuzzSubsetter implements FontSubsetter {

    /** {@code HB_SUBSET_FLAGS_NO_HINTING}: discards the hinting instructions. */
    private static final int FLAG_NO_HINTING = 0x1;

    private final HarfBuzzSubsetLibrary.HarfBuzz harfBuzz;

    HarfBuzzSubsetter(HarfBuzzSubsetLibrary.HarfBuzz harfBuzz) {
        this.harfBuzz = harfBuzz;
    }

    /**
     * Loads the native library and creates the subsetter.
     *
     * @return the subsetter, or empty if the library is not available
     */
    public static Optional<HarfBuzzSubsetter> create() {
        // JNA derives its default string encoding from the `native.encoding`
        // system property. On Windows that value (e.g. Cp1252) is not always
        // registered in the runtime — in native images the charset support is
        // limited — which makes JNA print a warning (or, in some builds, fail).
        // This program passes no strings to HarfBuzz, so forcing UTF-8 here has
        // no functional effect.
        System.setProperty("native.encoding", "UTF-8");
        return HarfBuzzSubsetLibrary.load().map(HarfBuzzSubsetter::new);
    }

    /**
     * Hint shown when the native library is not found.
     *
     * <p>It explains why the ASS will come out larger, how to install HarfBuzz
     * on the operating system in use, and how to point to a file explicitly.
     *
     * @return message ready for display
     */
    public static String installationHint() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        lines.add("Warning: the native HarfBuzz library (libharfbuzz-subset) was not "
                + "found; the font will be embedded without size reduction.");
        if (os.contains("win")) {
            lines.add("To reduce the ASS size, install HarfBuzz with its subsetting library "
                    + "(e.g. via MSYS2, package mingw-w64-ucrt-x86_64-harfbuzz) and keep its "
                    + "bin directory on the PATH, or provide the subsetting file with "
                    + HarfBuzzSubsetLibrary.OVERRIDE_ENV + "=<file>.");
        } else if (os.contains("mac") || os.contains("darwin")) {
            lines.add("To reduce the ASS size, install HarfBuzz: brew install harfbuzz.");
        } else {
            lines.add("To reduce the ASS size, install HarfBuzz:");
            lines.add("  - Debian/Ubuntu: sudo apt install libharfbuzz-subset0");
            lines.add("  - Fedora: sudo dnf install harfbuzz");
            lines.add("  - Arch: sudo pacman -S harfbuzz");
        }
        lines.add("Or point to the file with " + HarfBuzzSubsetLibrary.OVERRIDE_ENV
                + "=<file> or -D" + HarfBuzzSubsetLibrary.OVERRIDE_PROPERTY + "=<file>.");
        return String.join(System.lineSeparator(), lines);
    }

    @Override
    public byte[] subset(byte[] data, Set<Integer> codePoints) {
        if (data == null || data.length == 0 || codePoints == null || codePoints.isEmpty()) {
            return null;
        }
        try {
            return subsetWithLibrary(data, codePoints);
        } catch (UnsatisfiedLinkError e) {
            // A native function could not be resolved at call time (for example
            // a Windows DLL that does not export everything it should). Means
            // "subsetting unavailable": the caller embeds the full font.
            return null;
        }
    }

    private byte[] subsetWithLibrary(byte[] data, Set<Integer> codePoints) {
        Memory memory = new Memory(data.length);
        memory.write(0, data, 0, data.length);

        Pointer blob = harfBuzz.hb_blob_create(memory, data.length,
                HarfBuzzSubsetLibrary.HB_MEMORY_MODE_DUPLICATE, null, null);
        if (blob == null) {
            return null;
        }

        Pointer face = null;
        Pointer input = null;
        Pointer subsetFace = null;
        Pointer resultBlob = null;
        try {
            face = harfBuzz.hb_face_create(blob, 0);
            input = harfBuzz.hb_subset_input_create_or_fail();
            if (face == null || input == null) {
                return null;
            }

            Pointer unicodes = harfBuzz.hb_subset_input_unicode_set(input);
            if (unicodes == null) {
                return null;
            }
            for (Integer codePoint : codePoints) {
                harfBuzz.hb_set_add(unicodes, codePoint);
            }
            harfBuzz.hb_subset_input_set_flags(input, FLAG_NO_HINTING);

            subsetFace = harfBuzz.hb_subset_or_fail(face, input);
            if (subsetFace == null) {
                return null;
            }

            resultBlob = harfBuzz.hb_face_reference_blob(subsetFace);
            if (resultBlob == null) {
                return null;
            }
            IntByReference length = new IntByReference();
            Pointer output = harfBuzz.hb_blob_get_data(resultBlob, length);
            int size = length.getValue();
            if (output == null || size <= 0) {
                return null;
            }
            return output.getByteArray(0, size);
        } finally {
            if (resultBlob != null) {
                harfBuzz.hb_blob_destroy(resultBlob);
            }
            if (subsetFace != null) {
                harfBuzz.hb_face_destroy(subsetFace);
            }
            if (input != null) {
                harfBuzz.hb_subset_input_destroy(input);
            }
            if (face != null) {
                harfBuzz.hb_face_destroy(face);
            }
            harfBuzz.hb_blob_destroy(blob);
        }
    }
}
