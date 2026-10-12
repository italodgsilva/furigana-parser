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
 * Acesso à biblioteca nativa do HarfBuzz para subsetting
 * ({@code libharfbuzz-subset}).
 *
 * <p>O subsetting de fontes CFF/CID como a {@code Noto Sans CJK JP} exige uma
 * implementação especializada; o HarfBuzz é a referência e é o mesmo componente
 * usado por ferramentas como o {@code assfonts}. Aqui ele é acessado por JNA,
 * sem processo externo.
 *
 * <p>A biblioteca é carregada sob demanda. Como o nome do arquivo varia entre
 * sistemas (no Linux a instalação de runtime traz apenas o soname versionado,
 * ex.: {@code libharfbuzz-subset.so.0}), a busca tenta primeiro a resolução
 * padrão do JNA e depois varre os diretórios usuais. É possível apontar um
 * arquivo explícito pela propriedade {@value #OVERRIDE_PROPERTY} ou pela
 * variável de ambiente {@value #OVERRIDE_ENV}.
 *
 * <p>As funções {@code hb_blob_*} e {@code hb_face_*} vivem na biblioteca core
 * ({@code libharfbuzz}), que é dependência da biblioteca de subsetting — por
 * isso resolvem a partir do mesmo identificador carregado.
 */
final class HarfBuzzSubsetLibrary {

    /** Propriedade de sistema que aponta o arquivo da biblioteca. */
    static final String OVERRIDE_PROPERTY = "furigana.harfbuzz.subset";

    /** Variável de ambiente que aponta o arquivo da biblioteca. */
    static final String OVERRIDE_ENV = "FURIGANA_HARFBUZZ_SUBSET";

    /** Nome lógico usado na resolução padrão do JNA. */
    private static final String LIBRARY_NAME = "harfbuzz-subset";

    /** Modo de memória do HarfBuzz em que os bytes informados são copiados. */
    static final int HB_MEMORY_MODE_DUPLICATE = 0;

    private HarfBuzzSubsetLibrary() {
    }

    /** Funções do HarfBuzz usadas pelo subsetting. */
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

    /** Carrega a biblioteca usando os nomes e diretórios padrão do sistema. */
    static Optional<HarfBuzz> load() {
        return load(defaultNames(), defaultDirectories());
    }

    /**
     * Carrega a biblioteca tentando os nomes informados e, em seguida, varrendo
     * os diretórios em busca de um arquivo de subsetting.
     *
     * @param names       nomes lógicos ou caminhos a tentar primeiro
     * @param directories diretórios onde procurar o arquivo
     * @return a biblioteca carregada, ou vazio se não for encontrada
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
                // Diretório inacessível: ignora.
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
