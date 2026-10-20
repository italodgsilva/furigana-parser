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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@code native-image/awt/reflect-config.json}, the reflection metadata
 * the native GUI needs on every platform (see
 * {@code tools/generate-awt-jni-config.py}).
 *
 * <p>{@code GraphicsPrimitiveMgr} registers most of its render loops as
 * {@code GraphicsPrimitiveProxy} objects that load the real class with
 * {@code Class.forName(name).getDeclaredConstructor().newInstance()}. A native
 * image cannot load a class by name unless it was registered for reflection, so
 * without this file the first line, rectangle or glyph the window paints dies
 * with
 *
 * <pre>
 * java.lang.RuntimeException: java.lang.ClassNotFoundException:
 *     sun.java2d.loops.SetDrawLineANY
 * </pre>
 *
 * <p>Swing has the same habit: Nimbus keeps its painters as strings in the
 * look-and-feel defaults ({@code NimbusDefaults$LazyPainter}) and
 * {@code sun.awt.shell.ShellFolder} resolves its platform manager from a desktop
 * property, so the file chooser fails without them with
 * {@code ClassNotFoundException: javax.swing.plaf.nimbus.FileChooserPainter} or
 * {@code java.lang.Error: Could not instantiate Shell Folder Manager}.
 *
 * <p>Only the native build consumes the file, so nothing else would notice if
 * it went missing.
 */
class AwtReflectMetadataTest {

    /** The proxies registered by {@code GeneralRenderer} and {@code CustomComponent}. */
    private static final Set<String> REQUIRED = Set.of(
            "sun.java2d.loops.SetFillRectANY",
            "sun.java2d.loops.SetFillPathANY",
            "sun.java2d.loops.SetFillSpansANY",
            "sun.java2d.loops.SetDrawLineANY",
            "sun.java2d.loops.SetDrawPolygonsANY",
            "sun.java2d.loops.SetDrawPathANY",
            "sun.java2d.loops.SetDrawRectANY",
            "sun.java2d.loops.XorFillRectANY",
            "sun.java2d.loops.XorFillPathANY",
            "sun.java2d.loops.XorFillSpansANY",
            "sun.java2d.loops.XorDrawLineANY",
            "sun.java2d.loops.XorDrawPolygonsANY",
            "sun.java2d.loops.XorDrawPathANY",
            "sun.java2d.loops.XorDrawRectANY",
            "sun.java2d.loops.XorDrawGlyphListANY",
            "sun.java2d.loops.XorDrawGlyphListAAANY",
            "sun.java2d.loops.OpaqueCopyAnyToArgb",
            "sun.java2d.loops.OpaqueCopyArgbToAny",
            "sun.java2d.loops.XorCopyArgbToAny");

    /**
     * A sample of the Nimbus painters the window and the file chooser paint with.
     * The generator registers the hand-written classes of the package plus the
     * painters the JDK build generates from {@code skin.laf}; this list only has to
     * be representative enough to notice when that derivation is dropped.
     */
    private static final Set<String> REQUIRED_PAINTERS = Set.of(
            "javax.swing.plaf.nimbus.ButtonPainter",
            "javax.swing.plaf.nimbus.CheckBoxPainter",
            "javax.swing.plaf.nimbus.ComboBoxPainter",
            "javax.swing.plaf.nimbus.FileChooserPainter",
            "javax.swing.plaf.nimbus.InternalFramePainter",
            "javax.swing.plaf.nimbus.MenuBarMenuPainter",
            "javax.swing.plaf.nimbus.MenuBarPainter",
            "javax.swing.plaf.nimbus.MenuItemPainter",
            "javax.swing.plaf.nimbus.MenuPainter",
            "javax.swing.plaf.nimbus.OptionPaneMessageAreaOptionPaneLabelPainter",
            "javax.swing.plaf.nimbus.PopupMenuPainter",
            "javax.swing.plaf.nimbus.ProgressBarPainter",
            "javax.swing.plaf.nimbus.RadioButtonMenuItemPainter",
            "javax.swing.plaf.nimbus.RadioButtonPainter",
            "javax.swing.plaf.nimbus.ScrollBarPainter",
            "javax.swing.plaf.nimbus.ScrollBarThumbPainter",
            "javax.swing.plaf.nimbus.ScrollPanePainter",
            "javax.swing.plaf.nimbus.SeparatorPainter",
            "javax.swing.plaf.nimbus.SliderPainter",
            "javax.swing.plaf.nimbus.SplitPaneDividerPainter",
            "javax.swing.plaf.nimbus.SplitPanePainter",
            "javax.swing.plaf.nimbus.TabbedPanePainter",
            "javax.swing.plaf.nimbus.TableHeaderPainter",
            "javax.swing.plaf.nimbus.TableHeaderRendererPainter",
            "javax.swing.plaf.nimbus.TextFieldPainter",
            "javax.swing.plaf.nimbus.ToggleButtonPainter",
            "javax.swing.plaf.nimbus.ToolBarPainter",
            "javax.swing.plaf.nimbus.ToolTipPainter",
            "javax.swing.plaf.nimbus.TreeCellPainter",
            "javax.swing.plaf.nimbus.TreePainter");

    /** {@code ShellFolder} instantiates its manager reflectively, per platform. */
    private static final Set<String> REQUIRED_SHELL_MANAGERS = Set.of(
            "sun.awt.shell.ShellFolderManager",
            "sun.awt.shell.Win32ShellFolderManager2");

    @Test
    @DisplayName("The generated AWT reflection metadata registers the lazily loaded render loops")
    void awtReflectMetadataCoversPrimitiveProxies() throws IOException {
        Map<String, String> entries = entries(Files.readString(metadata(), StandardCharsets.UTF_8));

        for (String required : REQUIRED) {
            assertTrue(entries.containsKey(required), required + " is missing from the metadata");
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            // Class.forName needs the class registered, getDeclaredConstructor()
            // needs its (package-private) constructor.
            assertTrue(entry.getValue().contains("\"allDeclaredConstructors\": true"),
                    entry.getKey() + " must register its constructors");
        }
    }

    @Test
    @DisplayName("The generated AWT reflection metadata registers the painters and shell folder managers")
    void awtReflectMetadataCoversSwingNameLookups() throws IOException {
        Map<String, String> entries = entries(Files.readString(metadata(), StandardCharsets.UTF_8));

        for (String painter : REQUIRED_PAINTERS) {
            assertTrue(entries.containsKey(painter), painter + " is missing from the metadata");
        }
        for (String manager : REQUIRED_SHELL_MANAGERS) {
            assertTrue(entries.containsKey(manager), manager + " is missing from the metadata");
        }
        // The package classes plus the painters the JDK build generates from
        // skin.laf (63 on JDK 21): the names live in the compiled-in Nimbus
        // defaults, so a missing painter only breaks whichever component
        // happens to use it.
        assertTrue(entries.keySet().stream().filter(name -> name.startsWith("javax.swing.plaf.nimbus.")).count() >= 60,
                "the generated painters must be registered, not just a few hand-written classes");
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
            Path candidate = directory.resolve("native-image/awt/reflect-config.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new AssertionError("native-image/awt/reflect-config.json not found above the working directory");
    }
}
