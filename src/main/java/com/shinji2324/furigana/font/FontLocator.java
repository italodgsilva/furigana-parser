package com.shinji2324.furigana.font;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Localiza o arquivo de uma fonte instalada a partir do nome de família.
 *
 * <p>O ASS gerado referencia a fonte do template por nome (ex.:
 * {@code Noto Sans CJK JP}); para embutir o arquivo é preciso encontrá-lo no
 * sistema. A busca varre os diretórios usuais de fontes do Linux, do macOS e do
 * Windows e casa o nome de família lido da tabela {@code name} do próprio
 * arquivo — não o nome do arquivo.
 *
 * <p>Se uma face for encontrada dentro de uma coleção ({@code .ttc}), apenas
 * ela é recortada; o ASS embute uma face por vez.
 */
public final class FontLocator {

    /** Extensões reconhecidas durante a varredura. */
    private static final Set<String> FONT_EXTENSIONS = Set.of("ttf", "otf", "ttc", "otc");

    private final List<Path> directories;

    /** Cria o localizador com os diretórios usuais do sistema operacional. */
    public FontLocator() {
        this(defaultDirectories());
    }

    /**
     * Cria o localizador com diretórios informados (útil em testes).
     *
     * @param directories diretórios a varrer, na ordem
     */
    public FontLocator(List<Path> directories) {
        this.directories = List.copyOf(directories);
    }

    /**
     * Procura a primeira face cujo nome de família case com o informado.
     *
     * @param familyName nome de família (ex.: {@code Noto Sans CJK JP})
     * @return a fonte pronta para embutir, ou vazio se não encontrada
     */
    public Optional<FontFile> find(String familyName) {
        String target = normalize(familyName);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            Optional<FontFile> found = findInDirectory(directory, target);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Carrega uma face de um arquivo informado explicitamente.
     *
     * <p>Se o arquivo for uma coleção, escolhe a face cujo nome de família casa
     * com o informado; se não houver casamento e a coleção tiver uma única face,
     * usa essa face.
     *
     * @param path       arquivo de fonte
     * @param familyName família esperada
     * @return a fonte pronta para embutir
     * @throws IOException se o arquivo não puder ser lido ou nenhuma face servir
     */
    public FontFile read(Path path, String familyName) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Arquivo de fonte não encontrado: " + path);
        }
        byte[] data = Files.readAllBytes(path);
        List<SfntReader.Face> faces = SfntReader.read(data);
        if (faces.isEmpty()) {
            throw new IOException("Arquivo de fonte não reconhecido: " + path);
        }
        String target = normalize(familyName);
        for (SfntReader.Face face : faces) {
            if (matches(face, target)) {
                return toFontFile(path, face);
            }
        }
        if (faces.size() == 1) {
            return toFontFile(path, faces.get(0));
        }
        throw new IOException("A fonte " + path + " não contém a família '" + familyName + "'");
    }

    private Optional<FontFile> findInDirectory(Path directory, String target) {
        try (Stream<Path> paths = Files.walk(directory)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (!Files.isRegularFile(path) || !hasFontExtension(path)) {
                    continue;
                }
                byte[] data;
                try {
                    data = Files.readAllBytes(path);
                } catch (IOException e) {
                    continue;
                }
                for (SfntReader.Face face : SfntReader.read(data)) {
                    if (matches(face, target)) {
                        return Optional.of(toFontFile(path, face));
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // Diretório inacessível: segue para o próximo.
        }
        return Optional.empty();
    }

    private static boolean matches(SfntReader.Face face, String target) {
        for (String family : face.families()) {
            if (normalize(family).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static FontFile toFontFile(Path path, SfntReader.Face face) {
        return new FontFile(fileName(path, face), face.data());
    }

    private static String fileName(Path path, SfntReader.Face face) {
        String name = path.getFileName().toString();
        if (face.faceIndex() < 0) {
            return name;
        }
        // Face recortada de uma coleção: o conteúdo deixa de ser um .ttc.
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "-face" + face.faceIndex() + (face.cff() ? ".otf" : ".ttf");
    }

    private static boolean hasFontExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return FONT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * Normaliza um nome de família para comparação (minúsculas e espaços
     * colapsados).
     */
    static String normalize(String familyName) {
        if (familyName == null) {
            return "";
        }
        return familyName.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static List<Path> defaultDirectories() {
        List<Path> directories = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            Path base = Path.of(home);
            directories.add(base.resolve(".fonts"));
            directories.add(base.resolve(".local").resolve("share").resolve("fonts"));
            directories.add(base.resolve("Library").resolve("Fonts")); // macOS
            directories.add(base.resolve("AppData").resolve("Local")
                    .resolve("Microsoft").resolve("Windows").resolve("Fonts"));
        }
        directories.add(Path.of("/usr/share/fonts"));
        directories.add(Path.of("/usr/local/share/fonts"));
        directories.add(Path.of("/Library/Fonts")); // macOS
        directories.add(Path.of("/System/Library/Fonts"));
        String windir = System.getenv("WINDIR");
        if (windir != null && !windir.isBlank()) {
            directories.add(Path.of(windir, "Fonts"));
        }
        return directories;
    }
}
