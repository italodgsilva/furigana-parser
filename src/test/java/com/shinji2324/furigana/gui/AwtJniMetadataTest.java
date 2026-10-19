package com.shinji2324.furigana.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
            "sun.lwawt.macosx.CPlatformWindow",
            // GDIWindowSurfaceData.initIDs calls FindClass("java/lang/Thread")
            // and then the static Thread.currentThread().
            "java.lang.Thread",
            // awt_Toolkit.cpp and LWCToolkit.m look up java/lang/Runnable itself.
            "java.lang.Runnable",
            // The render queues resolve run() on the *concrete* Runnable object
            // (JNU_CallMethodByName on GetObjectClass(runnable)), and Native
            // Image refuses to answer that through java.lang.Runnable, so every
            // compiler-generated class the queues see has to be listed. These
            // are the Windows D3D/WGL paths that the Linux trace could not see.
            "sun.awt.windows.TranslucentWindowPainter$VIOptWindowPainter$1",
            "sun.font.StrikeCache$1",
            "sun.java2d.d3d.D3DGraphicsDevice$1",
            "sun.java2d.d3d.D3DRenderQueue$1",
            "sun.java2d.d3d.D3DSurfaceData$D3DDataBufferNative$1",
            "sun.java2d.opengl.WGLGraphicsConfig$1",
            "sun.java2d.pipe.BufferedTextPipe$1");

    /**
     * Packages whose classes live in java.base and must be registered with
     * their public members only. Registering their private internals aborts
     * every native build: {@code java.lang.System.initPhase1}/{@code System.props}
     * pull in {@code ClassLoader.initSystemClassLoader()}, which Native Image
     * deletes. (Everything else in the metadata is java.desktop.)
     */
    private static final Set<String> BASE_PACKAGES =
            Set.of("java.io.", "java.lang.", "java.math.", "java.text.", "java.util.");

    @Test
    @DisplayName("The generated AWT JNI metadata registers the native AWT classes")
    void awtJniMetadataCoversNativeAwt() throws IOException {
        Map<String, String> entries = entries(Files.readString(metadata(), StandardCharsets.UTF_8));

        assertTrue(entries.size() > 200,
                "expected the full AWT surface, found " + entries.size() + " classes");
        for (String required : REQUIRED) {
            assertTrue(entries.containsKey(required), required + " is missing from the metadata");
        }

        int publicOnly = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String name = entry.getKey();
            String body = entry.getValue();
            // FindClass only succeeds if the class is registered, and the following
            // GetMethodID/GetFieldID only succeed if the member is registered too.
            assertTrue(body.contains("\"allDeclaredConstructors\": true"),
                    name + " must register its constructors");
            assertTrue(body.contains("\"allPublicMethods\": true"),
                    name + " must register its public methods");
            if (BASE_PACKAGES.stream().anyMatch(name::startsWith)) {
                assertFalse(body.contains("\"allDeclaredMethods\": true"),
                        name + " is a java.base class and must not register its private methods");
                assertFalse(body.contains("\"allDeclaredFields\": true"),
                        name + " is a java.base class and must not register its private fields");
                publicOnly++;
            } else {
                assertTrue(body.contains("\"allDeclaredMethods\": true"),
                        name + " must register all of its declared methods");
                assertTrue(body.contains("\"allDeclaredFields\": true"),
                        name + " must register all of its declared fields");
            }
        }
        assertTrue(publicOnly > 0, "expected the java.base classes to be registered public-only");
    }

    /** Splits the top-level list into one {@code name -> body} entry per class. */
    private static Map<String, String> entries(String json) {
        Map<String, String> entries = new LinkedHashMap<>();
        Matcher objects = Pattern.compile("\\{[^{}]*}").matcher(json);
        while (objects.find()) {
            String body = objects.group();
            Matcher name = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            assertTrue(name.find(), "every entry must have a name: " + body);
            entries.put(name.group(1), body);
        }
        return entries;
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
