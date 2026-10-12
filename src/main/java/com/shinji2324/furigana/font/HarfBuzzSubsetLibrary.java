package com.shinji2324.furigana.font;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Access to the native HarfBuzz library for subsetting
 * ({@code libharfbuzz-subset}).
 *
 * <p>Subsetting CFF/CID fonts such as {@code Noto Sans CJK JP} requires a
 * specialized implementation; HarfBuzz is the reference and is the same
 * component used by tools such as {@code assfonts}. Here it is accessed through
 * JNA, without an external process.
 *
 * <p>The library is loaded on demand. Since the file name varies between
 * systems (on Linux the runtime installation ships only the versioned soname,
 * e.g. {@code libharfbuzz-subset.so.0}), the search first tries JNA's standard
 * resolution and then scans the usual directories. It is possible to point to
 * an explicit file through the {@value #OVERRIDE_PROPERTY} property or the
 * {@value #OVERRIDE_ENV} environment variable.
 *
 * <p>The {@code hb_blob_*} and {@code hb_face_*} functions live in the core
 * library ({@code libharfbuzz}), which is a dependency of the subsetting
 * library — that is why they resolve from the same loaded identifier.
 */
final class HarfBuzzSubsetLibrary {

    /** System property that points to the library file. */
    static final String OVERRIDE_PROPERTY = "furigana.harfbuzz.subset";

    /** Environment variable that points to the library file. */
    static final String OVERRIDE_ENV = "FURIGANA_HARFBUZZ_SUBSET";

    /** Logical name used in JNA's standard resolution. */
    private static final String LIBRARY_NAME = "harfbuzz-subset";

    /** HarfBuzz memory mode in which the given bytes are copied. */
    static final int HB_MEMORY_MODE_DUPLICATE = 0;

    private HarfBuzzSubsetLibrary() {
    }

    /** HarfBuzz functions used by subsetting. */
    interface HarfBuzz extends Library {

        Pointer hb_blob_create(Pointer data, int length, int mode, Pointer userData, Pointer destroy);

        Pointer hb_blob_get_data(Pointer blob, IntByReference length);

        void hb_blob_destroy(Pointer blob);

        Pointer hb_face_create(Pointer blob, int index);

        Pointer hb_face_reference_blob(Pointer face);

        void hb_face_destroy(Pointer face);

        Pointer hb_subset_input_create_or_fail();

        Pointer hb_subset_input_unicode_set(Pointer input);

        void hb_subset_input_set_flags(Pointer input, int flags);

        void hb_subset_input_destroy(Pointer input);

        Pointer hb_subset_or_fail(Pointer face, Pointer input);

        void hb_set_add(Pointer set, int codePoint);
    }

    /** Loads the library using the system's standard names and directories. */
    static Optional<HarfBuzz> load() {
        return load(defaultNames(), defaultDirectories());
    }

    /**
     * Loads the library by trying the given names and then scanning the
     * directories for a subsetting file.
     *
     * @param names       logical names or paths to try first
     * @param directories directories in which to look for the file
     * @return the loaded library, or empty if it is not found
     */
    static Optional<HarfBuzz> load(List<String> names, List<Path> directories) {
        for (String name : names) {
            HarfBuzz library = tryLoad(name);
            if (library != null) {
                return Optional.of(library);
            }
        }
        for (Path path : filesIn(directories)) {
            HarfBuzz library = tryLoad(path.toAbsolutePath().toString());
            if (library != null) {
                return Optional.of(library);
            }
        }
        return Optional.empty();
    }

    private static HarfBuzz tryLoad(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Native.load(name, HarfBuzz.class);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return null;
        }
    }

    private static List<String> defaultNames() {
        List<String> names = new ArrayList<>();
        String override = override();
        if (!override.isBlank()) {
            names.add(override);
        }
        names.add(LIBRARY_NAME);
        return names;
    }

    private static String override() {
        String property = System.getProperty(OVERRIDE_PROPERTY);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(OVERRIDE_ENV);
        return environment == null ? "" : environment;
    }

    private static List<Path> filesIn(List<Path> directories) {
        List<Path> files = new ArrayList<>();
        for (Path directory : directories) {
            if (directory == null || !Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(directory)) {
                entries.filter(Files::isRegularFile)
                        .filter(HarfBuzzSubsetLibrary::isSubsetLibrary)
                        .sorted()
                        .forEach(files::add);
            } catch (IOException e) {
                // Inaccessible directory: ignore it.
            }
        }
        return files;
    }

    private static boolean isSubsetLibrary(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.contains("harfbuzz") || !name.contains("subset")) {
            return false;
        }
        return name.endsWith(".so") || name.contains(".so.")
                || name.endsWith(".dylib") || name.endsWith(".dll");
    }

    private static List<Path> defaultDirectories() {
        List<Path> directories = new ArrayList<>();
        addPathList(directories, System.getenv("LD_LIBRARY_PATH"));
        addPathList(directories, System.getenv("DYLD_LIBRARY_PATH"));
        addPathList(directories, System.getProperty("java.library.path"));
        directories.add(Path.of("/usr/lib/x86_64-linux-gnu"));
        directories.add(Path.of("/usr/lib/aarch64-linux-gnu"));
        directories.add(Path.of("/usr/lib64"));
        directories.add(Path.of("/usr/lib"));
        directories.add(Path.of("/usr/local/lib"));
        directories.add(Path.of("/lib/x86_64-linux-gnu"));
        directories.add(Path.of("/lib"));
        directories.add(Path.of("/opt/homebrew/lib"));           // macOS (Apple Silicon)
        directories.add(Path.of("/usr/local/opt/harfbuzz/lib")); // macOS (Homebrew/Intel)
        directories.add(Path.of("/opt/local/lib"));              // macOS (MacPorts)
        return directories;
    }

    private static void addPathList(List<Path> directories, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String entry : value.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                directories.add(Path.of(entry));
            }
        }
    }
}
