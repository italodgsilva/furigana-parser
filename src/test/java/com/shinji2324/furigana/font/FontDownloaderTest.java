package com.shinji2324.furigana.font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link FontDownloader}.
 *
 * <p>The HTTP layer is replaced by a {@link ByteFetcher} that returns
 * pre-recorded bytes, keeping the tests deterministic and offline.
 */
class FontDownloaderTest {

    @TempDir
    Path tempDir;

    private static final byte[] FONT_BYTES = {0x4F, 0x54, 0x54, 0x4F, 1, 2, 3};

    @Test
    @DisplayName("Downloads and caches the default family, reusing the cache")
    void downloadsAndCaches() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        FontDownloader downloader = new FontDownloader(tempDir, url -> {
            calls.incrementAndGet();
            assertEquals(FontDownloader.DEFAULT_URL, url);
            return Optional.of(FONT_BYTES);
        });

        assertFalse(downloader.isCached());
        Optional<FontFile> first = downloader.obtain(FontDownloader.DEFAULT_FAMILY);
        assertTrue(first.isPresent());
        assertArrayEquals(FONT_BYTES, first.get().data());
        assertEquals(FontDownloader.FILE_NAME, first.get().fileName());
        assertArrayEquals(FONT_BYTES,
                Files.readAllBytes(tempDir.resolve(FontDownloader.FILE_NAME)));
        assertTrue(downloader.isCached());

        assertTrue(downloader.obtain(FontDownloader.DEFAULT_FAMILY).isPresent());
        assertEquals(1, calls.get()); // the cache is reused on the second call
    }

    @Test
    @DisplayName("Reuses an existing cached file without downloading")
    void reusesCache() throws IOException {
        Files.write(tempDir.resolve(FontDownloader.FILE_NAME), FONT_BYTES);
        FontDownloader downloader = new FontDownloader(tempDir, url -> {
            fail("must not download when the cache is present");
            return Optional.empty();
        });

        Optional<FontFile> font = downloader.obtain(FontDownloader.DEFAULT_FAMILY);
        assertTrue(font.isPresent());
        assertArrayEquals(FONT_BYTES, font.get().data());
    }

    @Test
    @DisplayName("Ignores families other than the default one")
    void ignoresOtherFamilies() {
        AtomicInteger calls = new AtomicInteger();
        FontDownloader downloader = new FontDownloader(tempDir, url -> {
            calls.incrementAndGet();
            return Optional.of(FONT_BYTES);
        });

        assertTrue(downloader.obtain("Yu Gothic").isEmpty());
        assertTrue(downloader.obtain("Noto Sans CJK KR").isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("Matches the default family case and whitespace insensitively")
    void normalizesFamily() {
        AtomicInteger calls = new AtomicInteger();
        FontDownloader downloader = new FontDownloader(tempDir, url -> {
            calls.incrementAndGet();
            return Optional.of(FONT_BYTES);
        });

        assertTrue(downloader.obtain("  noto   sans cjk jp ").isPresent());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("Returns empty when the download fails or the body is empty")
    void downloadFailures() {
        FontDownloader noNetwork = new FontDownloader(tempDir, url -> Optional.empty());
        assertTrue(noNetwork.obtain(FontDownloader.DEFAULT_FAMILY).isEmpty());

        FontDownloader emptyBody = new FontDownloader(tempDir, url -> Optional.of(new byte[0]));
        assertTrue(emptyBody.obtain(FontDownloader.DEFAULT_FAMILY).isEmpty());
    }

    @Test
    @DisplayName("Still embeds the bytes when the cache cannot be written")
    void usableWithoutCacheWrite() throws IOException {
        Path blocker = tempDir.resolve("fonts");
        Files.writeString(blocker, "not a directory");
        FontDownloader downloader = new FontDownloader(blocker, url -> Optional.of(FONT_BYTES));

        Optional<FontFile> font = downloader.obtain(FontDownloader.DEFAULT_FAMILY);
        assertTrue(font.isPresent());
        assertArrayEquals(FONT_BYTES, font.get().data());
    }
}