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
 * Locates the file of an installed font from its family name.
 *
 * <p>The generated ASS references the template font by name (e.g.
 * {@code Noto Sans CJK JP}); to embed the file it must first be found on the
 * system. The search scans the usual font directories of Linux, macOS and
 * Windows and matches the family name read from the file's own {@code name}
 * table — not the file name.
 *
 * <p>If a face is found inside a collection ({@code .ttc}), only that face is
 * extracted; ASS embeds one face at a time.
 */
public final class FontLocator {

    /** Extensions recognized during the scan. */
    private static final Set<String> FONT_EXTENSIONS = Set.of("ttf", "otf", "ttc", "otc");

    private final List<Path> directories;

    /** Creates the locator with the usual directories of the operating system. */
    public FontLocator() {
        this(defaultDirectories());
    }

    /**
     * Creates the locator with the given directories (useful in tests).
     *
     * @param directories directories to scan, in order
     */
    public FontLocator(List<Path> directories) {
        this.directories = List.copyOf(directories);
    }

    /**
     * Looks for the first face whose family name matches the given one.
     *
     * @param familyName family name (e.g. {@code Noto Sans CJK JP})
     * @return the font ready to embed, or empty if none is found
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
     * Loads a face from an explicitly given file.
     *
     * <p>If the file is a collection, picks the face whose family name matches
     * the given one; if there is no match and the collection has a single face,
     * it uses that face.
     *
     * @param path       font file
     * @param familyName expected family
     * @return the font ready to embed
     * @throws IOException if the file cannot be read or no face is usable
     */
    public FontFile read(Path path, String familyName) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Font file not found: " + path);
        }
        byte[] data = Files.readAllBytes(path);
        List<SfntReader.Face> faces = SfntReader.read(data);
        if (faces.isEmpty()) {
            throw new IOException("Unrecognized font file: " + path);
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
        throw new IOException("Font " + path + " does not contain family '" + familyName + "'");
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
            // Inaccessible directory: move on to the next one.
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
        // Face extracted from a collection: the content is no longer a .ttc.
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
     * Normalizes a family name for comparison (lowercase and collapsed
     * whitespace).
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
