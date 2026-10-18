package com.shinji2324.furigana.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@code native-image/awt/jni-config.json}, the JNI metadata the native
 * GUI needs on every platform (see {@code tools/generate-awt-jni-config.py}).
 * Only the native build consumes it, so nothing else would notice if it were
 * deleted or truncated, and the failure would only show up as a crash in the
 * released Windows/macOS binaries.
 */
class AwtJniMetadataTest {

    /** Classes the Windows/macOS AWT code looks up and the Linux trace missed. */
    private static final Set<String> REQUIRED = Set.of(
            "java.awt.Toolkit",                 // Toolkit.initIDs
            "java.awt.Insets",                  // WComponentPeer
            "java.awt.Point",                   // WComponentPeer.getLocationOnScreen
            "java.awt.event.KeyEvent",          // WToolkit.eventLoop
            "java.awt.event.MouseWheelEvent",   // WToolkit.eventLoop
            "sun.awt.windows.WToolkit",
            "sun.awt.windows.WComponentPeer",
            "sun.lwawt.macosx.LWCToolkit",
            "sun.lwawt.macosx.CPlatformWindow");

    @Test
    @DisplayName("The generated AWT JNI metadata registers the native AWT classes")
    void awtJniMetadataCoversNativeAwt() throws IOException {
        String json = Files.readString(metadata(), StandardCharsets.UTF_8);

        Set<String> registered = new LinkedHashSet<>();
        Matcher names = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        while (names.find()) {
            registered.add(names.group(1));
        }
        assertTrue(registered.size() > 200,
                "expected the full AWT surface, found " + registered.size() + " classes");
        for (String required : REQUIRED) {
            assertTrue(registered.contains(required), required + " is missing from the metadata");
        }

        // Every class has to register its members, otherwise FindClass succeeds
        // but the following GetMethodID/GetFieldID still throws NoSuchMethodError.
        assertEquals(registered.size(), count(json, "\"allDeclaredMethods\": true"),
                "every class must register all of its declared methods");
        assertEquals(registered.size(), count(json, "\"allDeclaredFields\": true"),
                "every class must register all of its declared fields");
        assertEquals(registered.size(), count(json, "\"allDeclaredConstructors\": true"),
                "every class must register all of its declared constructors");
    }

    private static int count(String haystack, String needle) {
        int total = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            total++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return total;
    }

    /** Surefire runs in the project directory, but IDEs do not always. */
    private static Path metadata() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("native-image/awt/jni-config.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new AssertionError("native-image/awt/jni-config.json not found above the working directory");
    }
}
