package com.shinji2324.furigana.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

/**
 * Downloads the default template font ({@link #DEFAULT_FAMILY}) when it is not
 * installed, so that {@code --embed-font} works on machines without the font
 * (e.g. Windows, which does not ship Noto Sans CJK).
 *
 * <p>The family searched is the one declared in the ASS template styles
 * ({@code /ass/default-style.ass}). The file is obtained from the official Noto
 * CJK repository and kept in the operating system's cache directory, so it is
 * downloaded once and reused by later runs.
 */
public final class FontDownloader {

    /** Family obtained when the installed fonts do not include it. */
    public static final String DEFAULT_FAMILY = "Noto Sans CJK JP";

    /** File name used for the cached copy. */
    public static final String FILE_NAME = "NotoSansCJKjp-Regular.otf";

    /** Official URL of the Japanese regular OTF. */
    public static final String DEFAULT_URL =
            "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/OTF/Japanese/"
                    + FILE_NAME;

    private final Path cacheDirectory;
    private final ByteFetcher fetcher;

    /** Creates the downloader with the OS cache directory and the real HTTP fetcher. */
    public FontDownloader() {
        this(defaultCacheDirectory(), ByteFetcher.defaultFetcher());
    }

    /**
     * Creates the downloader with explicitly configured cache and fetcher
     * (useful in tests).
     *
     * @param cacheDirectory directory where the downloaded font is cached
     * @param fetcher        HTTP fetcher
     */
    public FontDownloader(Path cacheDirectory, ByteFetcher fetcher) {
        this.cacheDirectory = cacheDirectory;
        this.fetcher = fetcher;
    }

    /**
     * Obtains the file of the default family, downloading and caching it when
     * necessary.
     *
     * @param familyName family to obtain
     * @return the font ready to embed, or empty if the family differs from the
     *         default one or the download fails
     */
    public Optional<FontFile> obtain(String familyName) {
        if (!isDefaultFamily(familyName)) {
            return Optional.empty();
        }
        Path file = cacheDirectory.resolve(FILE_NAME);
        if (Files.isRegularFile(file)) {
            Optional<FontFile> cached = read(file);
            if (cached.isPresent()) {
                return cached;
            }
        }
        Optional<byte[]> downloaded = fetcher.fetch(DEFAULT_URL);
        if (downloaded.isEmpty() || downloaded.get().length == 0) {
            return Optional.empty();
        }
        store(downloaded.get(), file);
        return Optional.of(new FontFile(FILE_NAME, downloaded.get()));
    }

    /**
     * Tells whether the downloaded font is already in the cache directory.
     *
     * @return true when a previously downloaded file exists
     */
    public boolean isCached() {
        return Files.isRegularFile(cacheDirectory.resolve(FILE_NAME));
    }

    /**
     * Tells whether the given family is the one downloaded by this class,
     * comparing case and whitespace insensitively.
     *
     * @param familyName family to compare
     * @return true when the family is {@link #DEFAULT_FAMILY}
     */
    public static boolean isDefaultFamily(String familyName) {
        return FontLocator.normalize(familyName).equals(FontLocator.normalize(DEFAULT_FAMILY));
    }

    /**
     * Returns the directory where the downloaded font is cached.
     *
     * @return cache directory
     */
    public Path cacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Default cache directory per operating system:
     * {@code %LOCALAPPDATA%\furigana-parser\fonts} on Windows,
     * {@code ~/Library/Caches/furigana-parser/fonts} on macOS, and
     * {@code $XDG_CACHE_HOME/furigana-parser/fonts} (or {@code ~/.cache/...}) on
     * Linux.
     *
     * @return cache directory
     */
    public static Path defaultCacheDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = defaultHome();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(home, "AppData", "Local");
            return base.resolve("furigana-parser").resolve("fonts");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Caches", "furigana-parser", "fonts");
        }
        String xdgCacheHome = System.getenv("XDG_CACHE_HOME");
        Path base = (xdgCacheHome != null && !xdgCacheHome.isBlank())
                ? Path.of(xdgCacheHome)
                : Path.of(home, ".cache");
        return base.resolve("furigana-parser").resolve("fonts");
    }

    private static String defaultHome() {
        String home = System.getProperty("user.home");
        return (home == null || home.isBlank()) ? ".furigana-parser" : home;
    }

    private static Optional<FontFile> read(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            if (data.length > 0) {
                return Optional.of(new FontFile(FILE_NAME, data));
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void store(byte[] data, Path file) {
        try {
            Files.createDirectories(cacheDirectory);
            Path temp = Files.createTempFile(cacheDirectory, FILE_NAME, ".part");
            try {
                Files.write(temp, data);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            // Cache write failed; the downloaded bytes are still usable for this run.
        }
    }
}