package com.shinji2324.furigana.font;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
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
import java.util.function.Predicate;
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
 * <p>On Unix, the {@code hb_blob_*} and {@code hb_face_*} functions of the core
 * library ({@code libharfbuzz}) resolve as dependencies of the subsetting
 * library, so a single load is enough. On Windows a loaded module does not
 * expose the functions of the DLLs it depends on, so the core and the subset
 * routines are looked up in two distinct libraries (see {@link #load}). When a
 * library is found but does not provide the expected symbols, it is rejected
 * and the search continues.
 */
final class HarfBuzzSubsetLibrary {

    /** System property that points to the library file. */
    static final String OVERRIDE_PROPERTY = "furigana.harfbuzz.subset";

    /** Environment variable that points to the library file. */
    static final String OVERRIDE_ENV = "FURIGANA_HARFBUZZ_SUBSET";

    /** Logical name used in JNA's standard resolution. */
    private static final String LIBRARY_NAME = "harfbuzz-subset";

    /** Logical names tried for the core library (Windows split loading). */
    private static final List<String> CORE_NAMES =
            List.of("harfbuzz", "libharfbuzz", "harfbuzz-0", "libharfbuzz-0");

    /** HarfBuzz memory mode in which the given bytes are copied. */
    static final int HB_MEMORY_MODE_DUPLICATE = 0;

    private HarfBuzzSubsetLibrary() {
    }

    /** HarfBuzz functions used by subsetting (core + subset combined view). */
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

    /** Functions of the core HarfBuzz library ({@code hb_blob_*}, {@code hb_face_*}, {@code hb_set_*}). */
    interface CoreHarfBuzz extends Library {

        Pointer hb_blob_create(Pointer data, int length, int mode, Pointer userData, Pointer destroy);

        Pointer hb_blob_get_data(Pointer blob, IntByReference length);

        void hb_blob_destroy(Pointer blob);

        Pointer hb_face_create(Pointer blob, int index);

        Pointer hb_face_reference_blob(Pointer face);

        void hb_face_destroy(Pointer face);

        void hb_set_add(Pointer set, int codePoint);
    }

    /** Subset API of HarfBuzz ({@code hb_subset_*}). */
    interface HarfBuzzSubsetApi extends Library {

        Pointer hb_subset_input_create_or_fail();

        Pointer hb_subset_input_unicode_set(Pointer input);

        void hb_subset_input_set_flags(Pointer input, int flags);

        void hb_subset_input_destroy(Pointer input);

        Pointer hb_subset_or_fail(Pointer face, Pointer input);
    }

    /** Loads the library using the system's standard names and directories. */
    static Optional<HarfBuzz> load() {
        return load(defaultNames(), defaultDirectories());
    }

    /**
     * Loads the library by trying the given names and then scanning the
     * directories for the subsetting file.
     *
     * <p>The combined attempt (a single library providing every function) covers
     * Unix, where the subsetting library makes the core symbols visible as
     * dependencies. On Windows the two pieces are separate DLLs, so the subset
     * routines and the core routines are looked up independently and combined.
     *
     * @param names       logical names or paths to try first
     * @param directories directories in which to look for the files
     * @return the loaded library, or empty if it is not found
     */
    static Optional<HarfBuzz> load(List<String> names, List<Path> directories) {
        List<Path> subsetFiles = subsetFilesIn(directories);
        List<Path> coreFiles = coreFilesIn(directories);

        for (String name : names) {
            HarfBuzz library = tryLoadCombined(name);
            if (library != null) {
                return Optional.of(library);
            }
        }
        for (Path path : subsetFiles) {
            HarfBuzz library = tryLoadCombined(path.toAbsolutePath().toString());
            if (library != null) {
                return Optional.of(library);
            }
        }

        for (String subsetName : names) {
            HarfBuzz library = tryLoadSplit(subsetName, coreFiles);
            if (library != null) {
                return Optional.of(library);
            }
        }
        for (Path subsetPath : subsetFiles) {
            HarfBuzz library = tryLoadSplit(subsetPath.toAbsolutePath().toString(), coreFiles);
            if (library != null) {
                return Optional.of(library);
            }
        }
        return Optional.empty();
    }

    private static HarfBuzz tryLoadCombined(String name) {
        HarfBuzz library = tryLoad(name, HarfBuzz.class);
        if (library == null || !hasSymbol(name, PROBE_SUBSET_SYMBOL)
                || !hasSymbol(name, PROBE_CORE_SYMBOL)) {
            return null;
        }
        return library;
    }

    private static HarfBuzz tryLoadSplit(String subsetName, List<Path> coreFiles) {
        HarfBuzzSubsetApi subset = tryLoad(subsetName, HarfBuzzSubsetApi.class);
        if (subset == null || !hasSymbol(subsetName, PROBE_SUBSET_SYMBOL)) {
            return null;
        }
        for (String coreName : CORE_NAMES) {
            CoreHarfBuzz core = tryLoad(coreName, CoreHarfBuzz.class);
            if (core != null && hasSymbol(coreName, PROBE_CORE_SYMBOL)) {
                return combine(core, subset);
            }
        }
        for (Path coreFile : coreFiles) {
            String corePath = coreFile.toAbsolutePath().toString();
            CoreHarfBuzz core = tryLoad(corePath, CoreHarfBuzz.class);
            if (core != null && hasSymbol(corePath, PROBE_CORE_SYMBOL)) {
                return combine(core, subset);
            }
        }
        return null;
    }

    /** A subset routine that must exist in a usable subsetting library. */
    private static final String PROBE_SUBSET_SYMBOL = "hb_subset_or_fail";

    /** A core routine that must exist in a usable set of libraries. */
    private static final String PROBE_CORE_SYMBOL = "hb_blob_create";

    /**
     * Tells whether a symbol exists in the library resolved from the given name
     * or path.
     *
     * <p>Resolving a function forces JNA to look it up now, turning an absent
     * symbol (common on Windows, where the core routines live in another DLL)
     * into a rejected candidate instead of a failure at call time.
     */
    private static boolean hasSymbol(String name, String symbol) {
        try {
            NativeLibrary.getInstance(name).getFunction(symbol);
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static <T extends Library> T tryLoad(String name, Class<T> type) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Native.load(name, type);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return null;
        }
    }

    /**
     * Combines the core and the subset libraries into the combined view used by
     * {@link HarfBuzzSubsetter}, so each function is resolved in the library
     * that exports it.
     */
    static HarfBuzz combine(CoreHarfBuzz core, HarfBuzzSubsetApi subset) {
        return new HarfBuzz() {
            @Override
            public Pointer hb_blob_create(Pointer data, int length, int mode,
                                          Pointer userData, Pointer destroy) {
                return core.hb_blob_create(data, length, mode, userData, destroy);
            }

            @Override
            public Pointer hb_blob_get_data(Pointer blob, IntByReference length) {
                return core.hb_blob_get_data(blob, length);
            }

            @Override
            public void hb_blob_destroy(Pointer blob) {
                core.hb_blob_destroy(blob);
            }

            @Override
            public Pointer hb_face_create(Pointer blob, int index) {
                return core.hb_face_create(blob, index);
            }

            @Override
            public Pointer hb_face_reference_blob(Pointer face) {
                return core.hb_face_reference_blob(face);
            }

            @Override
            public void hb_face_destroy(Pointer face) {
                core.hb_face_destroy(face);
            }

            @Override
            public Pointer hb_subset_input_create_or_fail() {
                return subset.hb_subset_input_create_or_fail();
            }

            @Override
            public Pointer hb_subset_input_unicode_set(Pointer input) {
                return subset.hb_subset_input_unicode_set(input);
            }

            @Override
            public void hb_subset_input_set_flags(Pointer input, int flags) {
                subset.hb_subset_input_set_flags(input, flags);
            }

            @Override
            public void hb_subset_input_destroy(Pointer input) {
                subset.hb_subset_input_destroy(input);
            }

            @Override
            public Pointer hb_subset_or_fail(Pointer face, Pointer input) {
                return subset.hb_subset_or_fail(face, input);
            }

            @Override
            public void hb_set_add(Pointer set, int codePoint) {
                core.hb_set_add(set, codePoint);
            }
        };
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

    private static List<Path> subsetFilesIn(List<Path> directories) {
        return filesIn(directories, HarfBuzzSubsetLibrary::isSubsetLibrary);
    }

    private static List<Path> coreFilesIn(List<Path> directories) {
        return filesIn(directories, HarfBuzzSubsetLibrary::isCoreLibrary);
    }

    private static List<Path> filesIn(List<Path> directories, Predicate<Path> filter) {
        List<Path> files = new ArrayList<>();
        for (Path directory : directories) {
            if (directory == null || !Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(directory)) {
                entries.filter(Files::isRegularFile)
                        .filter(filter)
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
        return name.contains("harfbuzz") && name.contains("subset")
                && isSharedLibraryFile(name);
    }

    private static boolean isCoreLibrary(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.contains("harfbuzz") || !isSharedLibraryFile(name)) {
            return false;
        }
        return !name.contains("subset") && !name.contains("icu")
                && !name.contains("gobject") && !name.contains("ahem");
    }

    private static boolean isSharedLibraryFile(String name) {
        return name.endsWith(".so") || name.contains(".so.")
                || name.endsWith(".dylib") || name.endsWith(".dll");
    }

    private static List<Path> defaultDirectories() {
        List<Path> directories = new ArrayList<>();
        addPathList(directories, System.getenv("PATH"));
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
        // Windows: MSYS2 and vcpkg put the HarfBuzz DLLs in their bin directories.
        directories.add(Path.of("C:\\msys64\\ucrt64\\bin"));
        directories.add(Path.of("C:\\msys64\\mingw64\\bin"));
        directories.add(Path.of("C:\\msys64\\clang64\\bin"));
        directories.add(Path.of("C:\\msys64\\msys64\\bin"));
        directories.add(Path.of("C:\\vcpkg\\installed\\x64-windows\\bin"));
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